package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate
import com.densmac.dashcam.domain.repository.SettingsRepository
import javax.inject.Inject

class UpdateSettingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun mic(enabled: Boolean) = settingsRepository.setMic(enabled)
    suspend fun osd(enabled: Boolean) = settingsRepository.setOsd(enabled)
    suspend fun loopDuration(duration: LoopDuration) = settingsRepository.setLoopDuration(duration)
    suspend fun speaker(level: LevelSetting) = settingsRepository.setSpeaker(level)
    suspend fun gSensor(level: LevelSetting) = settingsRepository.setGSensor(level)
    suspend fun timelapse(rate: TimelapseRate) = settingsRepository.setTimelapse(rate)
}
