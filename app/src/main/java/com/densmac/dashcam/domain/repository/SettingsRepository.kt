package com.densmac.dashcam.domain.repository

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamSettings
import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate

interface SettingsRepository {
    suspend fun getSettings(): AppResult<DashcamSettings>
    suspend fun setMic(enabled: Boolean): AppResult<Unit>
    suspend fun setOsd(enabled: Boolean): AppResult<Unit>
    suspend fun setRecording(enabled: Boolean): AppResult<Unit>
    suspend fun setLoopDuration(duration: LoopDuration): AppResult<Unit>
    suspend fun setSpeaker(level: LevelSetting): AppResult<Unit>
    suspend fun setGSensor(level: LevelSetting): AppResult<Unit>
    suspend fun setTimelapse(rate: TimelapseRate): AppResult<Unit>
    suspend fun switchCamera(camera: DashcamCamera, swappedMapping: Boolean = false): AppResult<Unit>
}
