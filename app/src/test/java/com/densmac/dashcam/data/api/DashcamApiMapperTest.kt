package com.densmac.dashcam.data.api

import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.kbToDisplayMb
import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashcamApiMapperTest {
    @Test
    fun sizeKbDisplayConversion() {
        assertEquals("2.0 MB", 2048L.kbToDisplayMb())
    }

    @Test
    fun settingValueMapping() {
        assertEquals(LoopDuration.THREE_MIN, LoopDuration.fromValue(2))
        assertEquals(LevelSetting.HIGH, LevelSetting.fromValue(3))
        assertEquals(TimelapseRate.TWO_SECONDS, TimelapseRate.fromValue(2))
    }

    @Test
    fun result98MapsToUnsupportedEndpoint() = runTest {
        val result = safeApiCall({ TestResponse(98, "unsupported") }) { Unit }
        assertTrue(result is AppResult.Failure)
        assertEquals(AppError.UnsupportedEndpoint, (result as AppResult.Failure).error)
    }

    @Test
    fun nonzeroResultMapsToApiError() = runTest {
        val result = safeApiCall({ TestResponse(7, "busy") }) { Unit }
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.ApiError)
    }
}

private data class TestResponse(
    override val result: Int,
    val info: String?
) : DashcamApiResponse {
    override fun infoText(): String? = info
}
