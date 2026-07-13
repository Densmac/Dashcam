package com.densmac.dashcam.data.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DashcamApiMockWebServerTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DashcamApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DashcamApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun getDeviceAttrSuccess() = runTest {
        server.enqueue(json("""{"result":0,"info":{"uuid":"GBB88102","softver":"20241030.093910","hwver":"V3.00","ssid":"DASHCAM","bssid":"DASHCAM","camnum":2,"curcamid":0}}"""))
        val response = api.getDeviceAttr()
        assertEquals(0, response.result)
        assertEquals("GBB88102", response.info?.uuid)
    }

    @Test
    fun getFileListLoopSuccess() = runTest {
        server.enqueue(json("""{"result":0,"info":[{"name":"/mnt/sdcard/VIDEO_F/20260713_192150_34_f.ts","duration":-1,"size":90112,"createtime":1783970510,"createtimestr":"20260713192150","type":2}]}"""))
        val response = api.getFileList("loop", 0, 199)
        assertEquals(1, response.info?.size)
        assertTrue(server.takeRequest().target.contains("folder=loop"))
    }

    @Test
    fun getFileListEmptyFolder() = runTest {
        server.enqueue(json("""{"result":0,"info":[]}"""))
        assertEquals(0, api.getFileList("park", 0, 99).info?.size)
    }

    @Test
    fun setParamValueSuccess() = runTest {
        server.enqueue(json("""{"result":0,"info":"success"}"""))
        assertEquals(0, api.setParamValue("mic", 1).result)
    }

    @Test
    fun setParamValueUnsupportedResult98() = runTest {
        server.enqueue(json("""{"result":98}"""))
        val result = safeApiCall({ api.setParamValue("gps", 1) }) { Unit }
        assertTrue(result is com.densmac.dashcam.core.common.AppResult.Failure)
    }

    @Test
    fun deleteSuccess() = runTest {
        server.enqueue(json("""{"result":0,"info":"delete success"}"""))
        assertEquals(0, api.deleteFile("/mnt/sdcard/test.jpg").result)
    }

    @Test
    fun deleteFailure() = runTest {
        server.enqueue(json("""{"result":5,"info":"busy"}"""))
        val result = safeApiCall({ api.deleteFile("/mnt/sdcard/test.jpg") }) { Unit }
        assertTrue(result is com.densmac.dashcam.core.common.AppResult.Failure)
    }

    private fun json(body: String): MockResponse =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
