package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.core.player.LivePreviewEngine
import com.densmac.dashcam.domain.repository.DashcamRepository
import javax.inject.Inject

class PrepareLivePreviewUseCase @Inject constructor(
    private val networkBinder: DashcamNetworkBinder,
    private val dashcamRepository: DashcamRepository,
    private val livePreviewEngine: LivePreviewEngine
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val bind = networkBinder.findAndBindDashcamNetwork()
        if (bind is AppResult.Failure) return bind
        val exitPlayback = dashcamRepository.exitPlayback()
        if (exitPlayback is AppResult.Failure) return exitPlayback
        val exitSettings = dashcamRepository.exitSettings()
        if (exitSettings is AppResult.Failure) return exitSettings
        val recorder = dashcamRepository.enterRecorder()
        if (recorder is AppResult.Failure) return recorder
        dashcamRepository.getMediaInfo()
        return livePreviewEngine.start(DashcamConstants.RTSP_TRACK2_URL)
    }
}
