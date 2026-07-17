package com.densmac.dashcam.data.stream

import com.densmac.dashcam.core.common.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A loopback HTTP proxy that makes the dashcam's recordings seekable for LibVLC.
 *
 * The camera serves raw `.ts` over HTTP and honours `Range` requests (206 Partial Content), but it
 * never advertises `Accept-Ranges: bytes` on a plain GET. LibVLC therefore treats the stream as a
 * non-seekable live source: the scrubber can't seek, playback position doesn't track, and EndReached
 * never fires. This proxy sits on `127.0.0.1`, forwards each request to the camera as a Range GET,
 * and re-emits the response with `Accept-Ranges: bytes` + a correct `Content-Length`, so LibVLC sees
 * an ordinary seekable file. (This mirrors how Viidure seeks: byte-range GETs against the same file.)
 */
@Singleton
class DashcamStreamProxy @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // streaming a large body; no read timeout
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var targetUrl: String? = null

    /**
     * Start proxying [cameraUrl] and return a `http://127.0.0.1:<port>/stream` URL to hand to
     * LibVLC. Any previously running proxy is stopped first.
     */
    @Synchronized
    fun start(cameraUrl: String): String {
        stop()
        targetUrl = cameraUrl
        val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        server = ss
        acceptThread = Thread({ acceptLoop(ss) }, "dashcam-stream-proxy").apply {
            isDaemon = true
            start()
        }
        val url = "http://127.0.0.1:${ss.localPort}/stream"
        Logger.d("DashcamStreamProxy started $url -> $cameraUrl")
        return url
    }

    @Synchronized
    fun stop() {
        targetUrl = null
        runCatching { server?.close() }
        server = null
        acceptThread = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val socket = try {
                ss.accept()
            } catch (t: Throwable) {
                break // server closed
            }
            Thread({ handleClient(socket) }, "dashcam-stream-proxy-conn").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handleClient(socket: Socket) {
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
                val url = targetUrl ?: return
                // Always ask the camera with a Range so it answers 206 and tells us the total size.
                val upstreamRange = range ?: "bytes=0-"
                val response = client(url, upstreamRange, headOnly = method == "HEAD") ?: run {
                    writeStatus(client.getOutputStream(), "502 Bad Gateway")
                    return
                }
                response.use { resp ->
                    val total = parseTotal(resp.header("Content-Range")) ?: resp.body?.contentLength() ?: -1
                    val partial = range != null
                    val out = client.getOutputStream()
                    val sb = StringBuilder()
                    sb.append("HTTP/1.1 ").append(if (partial) "206 Partial Content" else "200 OK").append("\r\n")
                    sb.append("Content-Type: video/mp2t\r\n")
                    sb.append("Accept-Ranges: bytes\r\n")
                    val bodyLength = resp.body?.contentLength() ?: -1
                    if (bodyLength >= 0) sb.append("Content-Length: ").append(bodyLength).append("\r\n")
                    if (partial && total > 0) {
                        val start = parseRangeStart(range)
                        val end = if (bodyLength >= 0) start + bodyLength - 1 else total - 1
                        sb.append("Content-Range: bytes ").append(start).append('-').append(end)
                            .append('/').append(total).append("\r\n")
                    }
                    sb.append("Connection: close\r\n\r\n")
                    out.write(sb.toString().toByteArray())
                    if (method != "HEAD") {
                        resp.body?.byteStream()?.let { pumpWithReadAhead(it, out) }
                    }
                    out.flush()
                }
            }.onFailure {
                // Client (LibVLC) hanging up mid-stream during a seek is normal; don't spam errors.
            }
        }
    }

    /**
     * Copy [source] (the camera) to [out] (LibVLC) through a bounded read-ahead buffer. A producer
     * thread pulls from the camera as fast as it delivers — filling up to READ_AHEAD_BYTES ahead of
     * playback — while the caller thread drains to LibVLC. This decouples the camera's bursty,
     * single-session delivery from LibVLC's consumption, so burst gaps are covered by the buffer
     * instead of stalling playback.
     */
    private fun pumpWithReadAhead(source: java.io.InputStream, out: OutputStream) {
        val queue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(READ_AHEAD_CHUNKS)
        val producer = Thread({
            val buf = ByteArray(CHUNK_BYTES)
            runCatching {
                while (true) {
                    val n = source.read(buf)
                    if (n < 0) break
                    if (n > 0) queue.put(buf.copyOf(n)) // blocks when full -> bounded read-ahead
                }
            }
            runCatching { queue.put(EOF) }
        }, "dashcam-stream-readahead").apply { isDaemon = true; start() }
        try {
            while (true) {
                val chunk = queue.take()
                if (chunk === EOF) break
                out.write(chunk)
            }
        } finally {
            producer.interrupt()
        }
    }

    private fun client(url: String, range: String, headOnly: Boolean): okhttp3.Response? = runCatching {
        val builder = Request.Builder().url(url).header("Range", range)
        if (headOnly) builder.head()
        client.newCall(builder.build()).execute()
    }.getOrNull()

    private fun writeStatus(out: OutputStream, status: String) {
        runCatching {
            out.write("HTTP/1.1 $status\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private fun parseTotal(contentRange: String?): Long? {
        // Content-Range: bytes 1000000-1001000/165195664
        val slash = contentRange?.substringAfterLast('/')?.trim() ?: return null
        return slash.toLongOrNull()
    }

    private fun parseRangeStart(range: String?): Long {
        // Range: bytes=1000000-  ->  1000000
        val spec = range?.substringAfter('=')?.trim() ?: return 0
        return spec.substringBefore('-').toLongOrNull() ?: 0
    }

    private companion object {
        const val CHUNK_BYTES = 64 * 1024
        // ~48 MB of read-ahead (768 x 64 KB): ~12 s of 4K video buffered ahead of playback. The
        // camera records 4K front+rear to the SD card while serving the stream from the same card,
        // so it stalls delivery for several seconds at a time; a deep buffer rides through those
        // gaps (whenever the camera can briefly deliver faster than 1x, this races ahead to refill).
        const val READ_AHEAD_CHUNKS = 768
        val EOF = ByteArray(0)
    }
}
