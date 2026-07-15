package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.domain.repository.DashcamRepository
import javax.inject.Inject

class PrepareLivePreviewUseCase @Inject constructor(
    private val networkBinder: DashcamNetworkBinder,
    private val dashcamRepository: DashcamRepository
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
        val mediaInfo = dashcamRepository.getMediaInfo()
        if (mediaInfo is AppResult.Failure) return mediaInfo
        return AppResult.Success(Unit)
    }
}
