package com.densmac.dashcam.data.stream

import android.content.Context
import com.densmac.dashcam.core.common.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * A loopback HTTP proxy that makes the dashcam's recordings seekable AND smooth for LibVLC.
 *
 * The camera is a single-session server that records 4K front+rear to the SD card while serving a
 * recording from that same card, so it delivers in bursts with multi-second stalls and it never
 * advertises `Accept-Ranges` on a plain GET. Streaming straight from it therefore stutters and can't
 * seek.
 *
 * This proxy instead **progressively downloads** the whole clip to a temp file in one continuous
 * camera connection (racing ahead of playback, resuming on drops) and serves LibVLC from that file.
 * LibVLC reads from local disk, so continuous playback never waits on the camera, and any byte
 * offset already on disk seeks instantly; only a forward seek past the download front waits. The
 * temp file is deleted when the stream stops.
 */
@Singleton
class DashcamStreamProxy @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS) // a stalled camera connection fails so we can reconnect
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var buffer: TempBuffer? = null

    /**
     * Start progressively buffering [cameraUrl] and return a `http://127.0.0.1:<port>/stream` URL to
     * hand to LibVLC. Any previously running stream is stopped first.
     */
    @Synchronized
    fun start(cameraUrl: String): String {
        stop()
        purgeStaleTempFiles()
        val buf = TempBuffer(context.cacheDir, cameraUrl, client).also { it.startDownloading() }
        buffer = buf
        val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = ss
        acceptThread = Thread({ acceptLoop(ss, buf) }, "dashcam-stream-proxy").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${ss.localPort}/stream"
        Logger.d("DashcamStreamProxy started $url -> $cameraUrl")
        return url
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        acceptThread = null
        buffer?.close()
        buffer = null
    }

    private fun purgeStaleTempFiles() {
        runCatching {
            context.cacheDir.listFiles { f -> f.name.startsWith(TEMP_PREFIX) }?.forEach { it.delete() }
        }
    }

    private fun acceptLoop(ss: ServerSocket, buf: TempBuffer) {
        while (!ss.isClosed) {
            val socket = try {
                ss.accept()
            } catch (t: Throwable) {
                break // server closed
            }
            Thread({ handleClient(socket, buf) }, "dashcam-stream-proxy-conn").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handleClient(socket: Socket, buf: TempBuffer) {
        socket.use { client ->
            runCatching {
                val input = client.getInputStream().bufferedReader()
                val requestLine = input.readLine() ?: return
                val method = requestLine.substringBefore(' ').trim().uppercase()
                var range: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        range = line.substringAfter(':').trim()
                    }
                }
                val out = client.getOutputStream()
                // We need the total size before we can answer; the downloader learns it from the
                // camera's first response.
                if (!buf.awaitTotalKnown()) {
                    writeStatus(out, "502 Bad Gateway")
                    return
                }
                val total = buf.total
                val start = parseRangeStart(range).coerceIn(0, total)
                val partial = range != null

                val sb = StringBuilder()
                sb.append("HTTP/1.1 ").append(if (partial) "206 Partial Content" else "200 OK").append("\r\n")
                sb.append("Content-Type: video/mp2t\r\n")
                sb.append("Accept-Ranges: bytes\r\n")
                sb.append("Content-Length: ").append(total - start).append("\r\n")
                if (partial) {
                    sb.append("Content-Range: bytes ").append(start).append('-').append(total - 1)
                        .append('/').append(total).append("\r\n")
                }
                sb.append("Connection: close\r\n\r\n")
                out.write(sb.toString().toByteArray())

                if (method == "HEAD") {
                    out.flush()
                    return
                }
                // Serve [start, total) from the temp file, blocking whenever we catch the download
                // front until more bytes land (or the download ends/fails).
                RandomAccessFile(buf.file, "r").use { raf ->
                    raf.seek(start)
                    var pos = start
                    val chunk = ByteArray(CHUNK_BYTES)
                    while (pos < total) {
                        val ready = buf.awaitAvailable(pos)
                        if (ready <= pos) break // fail/closed with no more data
                        val want = min(chunk.size.toLong(), min(ready, total) - pos).toInt()
                        val n = raf.read(chunk, 0, want)
                        if (n <= 0) break
                        out.write(chunk, 0, n)
                        pos += n
                    }
                    out.flush()
                }
            }.onFailure {
                // LibVLC closing a connection mid-stream (e.g. on seek) is normal; don't spam errors.
            }
        }
    }

    private fun writeStatus(out: OutputStream, status: String) {
        runCatching {
            out.write("HTTP/1.1 $status\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun parseRangeStart(range: String?): Long {
        // Range: bytes=1000000-  ->  1000000
        val spec = range?.substringAfter('=')?.trim() ?: return 0
        return spec.substringBefore('-').toLongOrNull() ?: 0
    }

    /**
     * A temp file that a background thread fills sequentially from the camera while readers stream
     * from it. Readers block on [awaitAvailable] until the requested offset has been downloaded.
     */
    private class TempBuffer(
        cacheDir: File,
        private val url: String,
        private val client: OkHttpClient
    ) {
        val file: File = File.createTempFile(TEMP_PREFIX, ".ts", cacheDir)
        private val lock = Object()
        @Volatile var total: Long = -1L
            private set
        @Volatile private var available: Long = 0L
        @Volatile private var failed = false
        @Volatile private var complete = false
        @Volatile private var closed = false
        private var downloader: Thread? = null

        fun startDownloading() {
            downloader = Thread({ download() }, "dashcam-stream-download").apply {
                isDaemon = true
                start()
            }
        }

        private fun download() {
            var attempt = 0
            RandomAccessFile(file, "rw").use { sink ->
                while (!closed && !complete) {
                    val from = available
                    try {
                        val request = Request.Builder().url(url).header("Range", "bytes=$from-").build()
                        client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) throw java.io.IOException("camera HTTP ${resp.code}")
                            if (total < 0) {
                                val parsed = resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                                val len = resp.body?.contentLength() ?: -1
                                total = parsed ?: if (len >= 0) from + len else -1
                                synchronized(lock) { lock.notifyAll() }
                            }
                            val src = resp.body?.byteStream() ?: throw java.io.IOException("no body")
                            sink.seek(from)
                            val buf = ByteArray(CHUNK_BYTES)
                            while (!closed) {
                                val n = src.read(buf)
                                if (n < 0) break
                                if (n > 0) {
                                    sink.write(buf, 0, n)
                                    synchronized(lock) {
                                        available += n
                                        lock.notifyAll()
                                    }
                                }
                            }
                        }
                        attempt = 0
                        if (total in 0..available) complete = true
                    } catch (t: Throwable) {
                        if (closed) break
                        attempt++
                        if (attempt > MAX_RETRIES) {
                            Logger.d("DashcamStreamProxy download failed after $attempt attempts: ${t.message}")
                            failed = true
                            synchronized(lock) { lock.notifyAll() }
                            break
                        }
                        runCatching { Thread.sleep(RETRY_DELAY_MS) }
                        // loop resumes the Range from wherever `available` reached.
                    }
                }
            }
            synchronized(lock) { lock.notifyAll() }
        }

        /** Block until the total size is known (from the camera's first response), or the download fails. */
        fun awaitTotalKnown(): Boolean {
            synchronized(lock) {
                while (!closed && !failed && total < 0) {
                    runCatching { lock.wait(TOTAL_WAIT_MS) }
                }
                return total >= 0
            }
        }

        /**
         * Block until more than [offset] bytes are available (so byte [offset] can be read), or the
         * download completes/fails/closes. Returns the number of bytes now available.
         */
        fun awaitAvailable(offset: Long): Long {
            synchronized(lock) {
                while (!closed && available <= offset && !complete && !failed) {
                    runCatching { lock.wait() }
                }
                return available
            }
        }

        fun close() {
            closed = true
            synchronized(lock) { lock.notifyAll() }
            downloader?.interrupt()
            runCatching { file.delete() }
        }
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        const val TEMP_PREFIX = "dashcam-stream"
        const val MAX_RETRIES = 5
        const val RETRY_DELAY_MS = 800L
        const val TOTAL_WAIT_MS = 10_000L
    }
}
