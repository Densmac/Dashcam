package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.domain.model.DashcamDevice
import com.densmac.dashcam.domain.repository.DashcamRepository
import javax.inject.Inject

class DetectDashcamUseCase @Inject constructor(
    private val networkBinder: DashcamNetworkBinder,
    private val dashcamRepository: DashcamRepository
) {
    suspend operator fun invoke(): AppResult<DashcamDevice> {
        return when (val bind = networkBinder.findAndBindDashcamNetwork()) {
            is AppResult.Success -> dashcamRepository.detectDevice()
            is AppResult.Failure -> bind
        }
    }
}
