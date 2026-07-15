package com.densmac.dashcam.domain.repository

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.data.api.MediaInfoDto
import com.densmac.dashcam.data.api.ProductInfoDto
import com.densmac.dashcam.domain.model.BatteryInfo
import com.densmac.dashcam.domain.model.DashcamDevice
import com.densmac.dashcam.domain.model.StorageStatus

interface DashcamRepository {
    suspend fun detectDevice(): AppResult<DashcamDevice>
    suspend fun getProductInfo(): AppResult<ProductInfoDto>
    suspend fun getMediaInfo(): AppResult<MediaInfoDto>
    suspend fun getRecDuration(): AppResult<Int>
    suspend fun enterRecorder(): AppResult<Unit>
    suspend fun enterPlayback(): AppResult<Unit>
    suspend fun exitPlayback(): AppResult<Unit>
    suspend fun exitSettings(): AppResult<Unit>
    suspend fun getStorageStatus(): AppResult<StorageStatus>
    suspend fun getBatteryInfo(): AppResult<BatteryInfo?>

    /** Rename the dashcam Wi-Fi AP. The AP reboots; the phone must reconnect to [ssid] afterwards. */
    suspend fun setWifiSsid(ssid: String): AppResult<Unit>

    /** Change the dashcam Wi-Fi password. The AP reboots; the phone must reconnect with [password]. */
    suspend fun setWifiPassword(password: String): AppResult<Unit>

    /** Format the dashcam SD card. Destructive: erases all footage. */
    suspend fun formatSdCard(): AppResult<Unit>
}
