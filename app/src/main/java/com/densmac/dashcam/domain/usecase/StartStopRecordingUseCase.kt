package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamSettings
import com.densmac.dashcam.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class StartStopRecordingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean): AppResult<DashcamSettings> {
        val set = settingsRepository.setRecording(enabled)
        if (set is AppResult.Failure) return set
        delay(300)
        return settingsRepository.getSettings()
    }
}
