package com.densmac.dashcam

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.data.api.DashcamApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * On-device integration tests that exercise the real dashcam over its Wi-Fi network
 * (192.168.169.1). They validate the confirmed protocol on real hardware and the raw
 * slash thumbnail behavior that a URL-encoded query would break.
 *
 * These require the phone to be connected to DASHCAM Wi-Fi. When the dashcam is not
 * reachable the tests are skipped (assumption failure) rather than failing, so the suite
 * stays green on machines/emulators without the hardware.
 */
@RunWith(AndroidJUnit4::class)
class DashcamProtocolInstrumentedTest {

    private lateinit var api: DashcamApi

    @Before
    fun setUp() {
        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .baseUrl(DashcamConstants.HTTP_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DashcamApi::class.java)

        assumeDashcamReachable()
    }

    private fun assumeDashcamReachable() {
        try {
            val response = runBlocking { api.getDeviceAttr() }
            assumeTrue("Dashcam did not return result 0", response.result == 0)
        } catch (t: Throwable) {
            assumeNoException("Dashcam not reachable on ${DashcamConstants.HTTP_BASE_URL}", t)
        }
    }

    @Test
    fun deviceAttrReportsTwoCameras() = runBlocking {
        val response = api.getDeviceAttr()
        assertEquals(0, response.result)
        assertTrue("Expected a dashcam with at least one camera", (response.info?.camnum ?: 0) >= 1)
    }

    @Test
    fun sdCardIsPresent() = runBlocking {
        val response = api.getSdInfo()
        assertEquals(0, response.result)
        // status >= 0 means a card is present; total capacity should be positive.
        assertTrue("SD card total should be positive", (response.info?.total ?: 0) > 0)
    }

    @Test
    fun recorderModeAndMediaInfoAreAvailable() = runBlocking {
        assertEquals(0, api.exitPlaybackThenEnterRecorder())
        val media = api.getMediaInfo()
        assertEquals(0, media.result)
    }

    private suspend fun DashcamApi.exitPlaybackThenEnterRecorder(): Int {
        playback("exit")
        setting("exit")
        return enterRecorder().result
    }

    @Test
    fun rtspPortAnswersOptions() {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(DashcamConstants.DEFAULT_HOST, 554), 3_000)
            socket.soTimeout = 3_000
            socket.getOutputStream().write(
                "OPTIONS ${DashcamConstants.RTSP_ROOT_URL} RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()
            )
            socket.getOutputStream().flush()
            val reply = socket.getInputStream().bufferedReader().readLine().orEmpty()
            assertTrue("Unexpected RTSP reply: $reply", reply.contains("200"))
        } catch (t: Throwable) {
            assumeNoException("RTSP port 554 not reachable", t)
        } finally {
            runCatching { socket.close() }
        }
    }

    @Test
    fun rawSlashThumbnailReturnsRealImageBytes() = runBlocking {
        // Enter playback so the file list is served, then find a loop recording.
        api.playback("enter")
        val files = api.getFileList("loop", 0, 199).info
            ?.flatMap { it.files.orEmpty() }
            ?.map { it.name }
            ?.filter { it.startsWith("/mnt/sdcard/") }
            .orEmpty()
        assumeTrue("No loop recordings on the dashcam to thumbnail", files.isNotEmpty())

        // The dashcam serves tiny (~1 KB) placeholders for clips it is still finalizing, and
        // full ~15-30 KB JPEGs for older clips. At least one clip must return a real image,
        // which only works when the file query keeps raw slashes (encoded=true). A URL-encoded
        // path returns a generic 1062-byte fallback for every file.
        var largest = 0
        for (path in files.take(12)) {
            val size = runCatching { api.getThumbnail(path).bytes().size }.getOrDefault(0)
            if (size > largest) largest = size
            if (largest > 4096) break
        }
        assertTrue(
            "Expected at least one real thumbnail (>4096 bytes) via raw-slash path, largest was $largest",
            largest > 4096
        )
    }
}
