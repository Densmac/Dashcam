package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.Logger
import com.densmac.dashcam.core.player.LivePreviewEngine
import com.densmac.dashcam.core.player.LivePreviewState
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
        val streamAlreadyPlaying = livePreviewEngine.state.value == LivePreviewState.Playing
        if (streamAlreadyPlaying) {
            // The dashcam serves a single RTSP session and swaps the active camera into
            // that same stream in place. Tearing the session down and rebuilding it on
            // every switch makes the dashcam stall the new PLAY (feed dies after a few
            // switches), so when the stream is live we only send switchcam and let the
            // existing session show the new feed.
            val switched = settingsRepository.switchCamera(camera, preferences.cameraMappingSwapped)
            if (switched is AppResult.Failure) return switched
            preferencesDataSource.setPreferredCamera(camera)
            return AppResult.Success(Unit)
        }
        // Nothing is playing yet (initial live start): switch the camera, re-enter recorder
        // mode, let the encoder settle, then open the RTSP session.
        livePreviewEngine.stop()
        delay(250)
        val result = settingsRepository.switchCamera(camera, preferences.cameraMappingSwapped)
        if (result is AppResult.Failure) return result
        preferencesDataSource.setPreferredCamera(camera)
        delay(300)
        val preflight = enterRecorderPreflight()
        if (preflight is AppResult.Failure) return preflight
        return startRtspWithSingleRetry()
    }

    private suspend fun enterRecorderPreflight(): AppResult<Unit> {
        val exitPlayback = dashcamRepository.exitPlayback()
        if (exitPlayback is AppResult.Failure) return exitPlayback
        val exitSettings = dashcamRepository.exitSettings()
        if (exitSettings is AppResult.Failure) return exitSettings
        val recorder = dashcamRepository.enterRecorder()
        if (recorder is AppResult.Failure) return recorder
        val recDuration = dashcamRepository.getRecDuration()
        if (recDuration is AppResult.Failure) return recDuration
        val mediaInfo = dashcamRepository.getMediaInfo()
        if (mediaInfo is AppResult.Failure) return mediaInfo
        return AppResult.Success(Unit)
    }

    private suspend fun startRtspWithSingleRetry(): AppResult<Unit> {
        val firstAttempt = livePreviewEngine.start(DashcamConstants.RTSP_ROOT_URL)
        if (firstAttempt is AppResult.Success) return firstAttempt
        Logger.d("RTSP start failed after camera switch; running one recovery preflight")
        delay(800)
        val preflight = enterRecorderPreflight()
        if (preflight is AppResult.Failure) return preflight
        return livePreviewEngine.start(DashcamConstants.RTSP_ROOT_URL)
    }
}
