package com.densmac.dashcam.data.stream

import com.densmac.dashcam.core.common.DispatchersProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives a streamed clip's duration the way a media player does for a headerless MPEG-TS file: read
 * the first PTS near the start and the last PTS near the end (via HTTP Range) and subtract. The
 * dashcam's file list reports `duration: -1` and LibVLC can't measure a live `.ts`, so without this
 * the streaming scrubber shows no total time. (Viidure shows "00:42" the same way.)
 *
 * Must run before the stream starts — the camera is single-session, so probing while LibVLC is
 * streaming would contend for the one connection.
 */
@Singleton
class TsDurationProbe @Inject constructor(
    private val dispatchers: DispatchersProvider
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Returns the clip duration in ms, or 0 if it can't be determined. */
    suspend fun probeDurationMs(cameraUrl: String): Long = withContext(dispatchers.io) {
        runCatching {
            val head = fetchRange(cameraUrl, 0, HEAD_BYTES - 1L) ?: return@withContext 0L
            val total = head.total
            if (total <= 0) return@withContext 0L
            val firstPts = scanPts(head.bytes, last = false) ?: return@withContext 0L
            val tailStart = (total - TAIL_BYTES).coerceAtLeast(0)
            val tail = fetchRange(cameraUrl, tailStart, total - 1) ?: return@withContext 0L
            val lastPts = scanPts(tail.bytes, last = true) ?: return@withContext 0L
            var ticks = lastPts - firstPts
            if (ticks < 0) ticks += (1L shl 33) // 33-bit PTS wraparound
            ticks / 90 // 90 kHz clock -> ms
        }.getOrDefault(0L)
    }

    private data class RangeResult(val bytes: ByteArray, val total: Long)

    private fun fetchRange(url: String, start: Long, end: Long): RangeResult? {
        val request = Request.Builder().url(url).header("Range", "bytes=$start-$end").build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            val total = resp.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                ?: (start + bytes.size)
            return RangeResult(bytes, total)
        }
    }

    /** Scan aligned TS packets for a video PES PTS; [last]=true returns the final one found. */
    private fun scanPts(buf: ByteArray, last: Boolean): Long? {
        val align = findAlignment(buf)
        if (align < 0) return null
        var i = align
        var found: Long? = null
        while (i + TS_PACKET <= buf.size) {
            val pts = packetPts(buf, i)
            if (pts != null) {
                if (!last) return pts
                found = pts
            }
            i += TS_PACKET
        }
        return found
    }

    /** Locate the byte offset (0 until 188) where three consecutive sync bytes line up. */
    private fun findAlignment(buf: ByteArray): Int {
        for (o in 0 until TS_PACKET) {
            if (o + 2 * TS_PACKET < buf.size &&
                buf[o].toInt() and 0xFF == SYNC &&
                buf[o + TS_PACKET].toInt() and 0xFF == SYNC &&
                buf[o + 2 * TS_PACKET].toInt() and 0xFF == SYNC
            ) return o
        }
        return -1
    }

    private fun packetPts(buf: ByteArray, off: Int): Long? {
        if (buf[off].toInt() and 0xFF != SYNC) return null
        val payloadUnitStart = (buf[off + 1].toInt() and 0x40) != 0
        if (!payloadUnitStart) return null
        val adaptation = (buf[off + 3].toInt() and 0x30) shr 4
        if (adaptation == 0x2) return null // adaptation only, no payload
        var payload = off + 4
        if (adaptation == 0x3) payload = off + 5 + (buf[off + 4].toInt() and 0xFF)
        // PES start code 00 00 01
        if (payload + 14 > off + TS_PACKET) return null
        if (buf[payload].toInt() and 0xFF != 0x00 ||
            buf[payload + 1].toInt() and 0xFF != 0x00 ||
            buf[payload + 2].toInt() and 0xFF != 0x01
        ) return null
        val streamId = buf[payload + 3].toInt() and 0xFF
        if (streamId < 0xE0 || streamId > 0xEF) return null // video stream only
        val ptsDtsFlags = (buf[payload + 7].toInt() and 0xC0) shr 6
        if (ptsDtsFlags != 0x2 && ptsDtsFlags != 0x3) return null
        val p = payload + 9
        if (p + 5 > off + TS_PACKET) return null
        val b0 = buf[p].toInt() and 0xFF
        val b1 = buf[p + 1].toInt() and 0xFF
        val b2 = buf[p + 2].toInt() and 0xFF
        val b3 = buf[p + 3].toInt() and 0xFF
        val b4 = buf[p + 4].toInt() and 0xFF
        return ((b0.toLong() and 0x0E) shl 29) or
            (b1.toLong() shl 22) or
            ((b2.toLong() and 0xFE) shl 14) or
            (b3.toLong() shl 7) or
            (b4.toLong() shr 1)
    }

    private companion object {
        const val TS_PACKET = 188
        const val SYNC = 0x47
        const val HEAD_BYTES = 128 * 1024L
        const val TAIL_BYTES = 512 * 1024L
    }
}
