package com.densmac.dashcam.data.repository

import android.net.Network
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.data.api.BatteryInfoResponse
import com.densmac.dashcam.data.api.CapabilityResponse
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.api.DeviceAttrResponse
import com.densmac.dashcam.data.api.FileListResponse
import com.densmac.dashcam.data.api.MediaInfoResponse
import com.densmac.dashcam.data.api.ParamItemDto
import com.densmac.dashcam.data.api.ParamItemsResponse
import com.densmac.dashcam.data.api.ParamValueDto
import com.densmac.dashcam.data.api.ParamValueResponse
import com.densmac.dashcam.data.api.ParamValuesResponse
import com.densmac.dashcam.data.api.ProductInfoResponse
import com.densmac.dashcam.data.api.RecDurationResponse
import com.densmac.dashcam.data.api.SdInfoResponse
import com.densmac.dashcam.data.api.StringInfoResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryImplTest {
    private val repository = SettingsRepositoryImpl(FakeDashcamApi(), FakeNetworkBinder())

    @Test
    fun resolutionLabelUsesCurrentValueIndex() {
        val settings = repository.mapSettings(
            items = listOf(
                ParamItemDto(
                    name = "rec_resolution",
                    items = listOf("4K+1080P", "2K+1080P"),
                    index = listOf(3, 4)
                )
            ),
            values = listOf(ParamValueDto(name = "rec_resolution", value = 4))
        )

        assertEquals("2K+1080P", settings.resolutionLabel)
    }

    @Test
    fun resolutionLabelRequiresCurrentValue() {
        val settings = repository.mapSettings(
            items = listOf(ParamItemDto(name = "rec_resolution", items = listOf("4K+1080P"), index = listOf(3))),
            values = emptyList()
        )

        assertNull(settings.resolutionLabel)
    }

    @Test
    fun resolutionLabelRequiresSupportedIndex() {
        val settings = repository.mapSettings(
            items = listOf(ParamItemDto(name = "rec_resolution", items = listOf("4K+1080P"), index = listOf(3))),
            values = listOf(ParamValueDto(name = "rec_resolution", value = 0))
        )

        assertNull(settings.resolutionLabel)
    }
}

private class FakeNetworkBinder : DashcamNetworkBinder {
    override val boundNetwork: StateFlow<Network?> = MutableStateFlow(null)
    override suspend fun findAndBindDashcamNetwork(): AppResult<Network> = error("Not used")
    override fun unbind() = Unit
}

private class FakeDashcamApi : DashcamApi {
    override suspend fun getDeviceAttr(): DeviceAttrResponse = error("Not used")
    override suspend fun getProductInfo(): ProductInfoResponse = error("Not used")
    override suspend fun getCapability(): CapabilityResponse = error("Not used")
    override suspend fun getMediaInfo(): MediaInfoResponse = error("Not used")
    override suspend fun playback(param: String): StringInfoResponse = error("Not used")
    override suspend fun setting(param: String): StringInfoResponse = error("Not used")
    override suspend fun enterRecorder(): StringInfoResponse = error("Not used")
    override suspend fun getParamItems(param: String): ParamItemsResponse = error("Not used")
    override suspend fun getParamValues(param: String): ParamValuesResponse = error("Not used")
    override suspend fun getParamValue(param: String): ParamValueResponse = error("Not used")
    override suspend fun setParamValue(param: String, value: Int): StringInfoResponse = error("Not used")
    override suspend fun getSdInfo(): SdInfoResponse = error("Not used")
    override suspend fun getRecDuration(): RecDurationResponse = error("Not used")
    override suspend fun getBatteryInfo(): BatteryInfoResponse = error("Not used")
    override suspend fun snapshot(): StringInfoResponse = error("Not used")
    override suspend fun getFileList(folder: String, start: Int, end: Int): FileListResponse = error("Not used")
    override suspend fun getThumbnail(fullPath: String): ResponseBody = error("Not used")
    override suspend fun deleteFile(fullPath: String): StringInfoResponse = error("Not used")
}
