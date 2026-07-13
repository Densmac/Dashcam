package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.player.LivePreviewEngine
import com.densmac.dashcam.data.datastore.UserPreferencesDataSource
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.densmac.dashcam.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SwitchCameraUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dashcamRepository: DashcamRepository,
    private val livePreviewEngine: LivePreviewEngine,
    private val preferencesDataSource: UserPreferencesDataSource
) {
    suspend operator fun invoke(camera: DashcamCamera): AppResult<Unit> {
        val preferences = preferencesDataSource.preferences.first()
        livePreviewEngine.stop()
        val result = settingsRepository.switchCamera(camera, preferences.cameraMappingSwapped)
        if (result is AppResult.Failure) return result
        preferencesDataSource.setPreferredCamera(camera)
        delay(300)
        val recorder = dashcamRepository.enterRecorder()
        if (recorder is AppResult.Failure) return recorder
        return livePreviewEngine.start(DashcamConstants.RTSP_TRACK2_URL)
    }
}
