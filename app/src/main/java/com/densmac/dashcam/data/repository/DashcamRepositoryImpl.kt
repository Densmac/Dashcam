package com.densmac.dashcam.data.repository

import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.api.DashcamApiMapper
import com.densmac.dashcam.data.api.MediaInfoDto
import com.densmac.dashcam.data.api.ProductInfoDto
import com.densmac.dashcam.data.api.safeApiCall
import com.densmac.dashcam.data.db.DeviceEntity
import com.densmac.dashcam.data.db.dao.DeviceDao
import com.densmac.dashcam.data.datastore.UserPreferencesDataSource
import com.densmac.dashcam.domain.model.BatteryInfo
import com.densmac.dashcam.domain.model.DashcamDevice
import com.densmac.dashcam.domain.model.StorageStatus
import com.densmac.dashcam.domain.repository.DashcamRepository
import com.google.gson.JsonParseException
import javax.inject.Inject

class DashcamRepositoryImpl @Inject constructor(
    private val api: DashcamApi,
    private val mapper: DashcamApiMapper,
    private val deviceDao: DeviceDao,
    private val preferencesDataSource: UserPreferencesDataSource,
    private val networkBinder: DashcamNetworkBinder
) : DashcamRepository {
    override suspend fun detectDevice(): AppResult<DashcamDevice> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        val attrResult = deviceAttr()
        if (attrResult is AppResult.Failure) return attrResult
        val attr = (attrResult as AppResult.Success).data
        val product = when (val productResult = productInfo()) {
            is AppResult.Success -> productResult.data
            is AppResult.Failure -> null
        }
        val device = mapper.device(attr, product)
        runCatching {
            deviceDao.upsert(
                DeviceEntity(
                    uuid = device.uuid,
                    model = device.model,
                    softwareVersion = device.softwareVersion,
                    hardwareVersion = device.hardwareVersion,
                    ssid = device.ssid,
                    cameraCount = device.cameraCount,
                    soc = device.soc,
                    lastSeenAt = System.currentTimeMillis()
                )
            )
            preferencesDataSource.setLastKnownDeviceUuid(device.uuid)
        }
        return AppResult.Success(device)
    }

    override suspend fun getProductInfo(): AppResult<ProductInfoDto> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return productInfo()
    }

    override suspend fun getMediaInfo(): AppResult<MediaInfoDto> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.getMediaInfo() }) { required(it.info) }
    }

    override suspend fun getRecDuration(): AppResult<Int> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.getRecDuration() }) { required(it.info).duration }
    }

    override suspend fun enterRecorder(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.enterRecorder() }) { Unit }
    }

    override suspend fun enterPlayback(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.playback("enter") }) { Unit }
    }

    override suspend fun exitPlayback(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.playback("exit") }) { Unit }
    }

    override suspend fun exitSettings(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.setting("exit") }) { Unit }
    }

    override suspend fun getStorageStatus(): AppResult<StorageStatus> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.getSdInfo() }) { mapper.storage(required(it.info)) }
    }

    override suspend fun getBatteryInfo(): AppResult<BatteryInfo?> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        return safeApiCall({ api.getBatteryInfo() }) { mapper.battery(it.info) }
    }

    override suspend fun setWifiSsid(ssid: String): AppResult<Unit> =
        setWifiAndReboot { safeApiCall({ api.setWifiSsid(ssid) }) { Unit } }

    override suspend fun setWifiPassword(password: String): AppResult<Unit> =
        setWifiAndReboot { safeApiCall({ api.setWifiPassword(password) }) { Unit } }

    // Mirrors Viidure's captured sequence: enter settings, setwifi (returns "set success"),
    // then wifireboot to apply. wifireboot returns {result: 98} and drops the AP connection,
    // so its result/errors are ignored — the reboot is a fire-and-forget side effect.
    private suspend fun setWifiAndReboot(setWifi: suspend () -> AppResult<Unit>): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        val enter = safeApiCall({ api.setting("enter") }) { Unit }
        if (enter is AppResult.Failure) return enter
        val set = setWifi()
        if (set is AppResult.Failure) return set
        runCatching { api.wifiReboot() }
        return AppResult.Success(Unit)
    }

    override suspend fun formatSdCard(): AppResult<Unit> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        // Match Viidure: stop recording, enter settings, then sdformat?index=1.
        runCatching { api.setParamValue("rec", 0) }
        val enter = safeApiCall({ api.setting("enter") }) { Unit }
        if (enter is AppResult.Failure) return enter
        return when (val result = safeApiCall({ api.formatSdCard(1) }) { Unit }) {
            is AppResult.Success -> result
            // A full-card format can outlast the HTTP timeout; since settings-enter already
            // proved the camera is reachable, treat a timeout as "format in progress".
            is AppResult.Failure ->
                if (result.error is AppError.DashcamApiUnreachable) AppResult.Success(Unit) else result
        }
    }

    private suspend fun ensureBound(): AppResult<Unit> =
        when (val bind = networkBinder.findAndBindDashcamNetwork()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> bind
        }

    private suspend fun deviceAttr() =
        safeApiCall({ api.getDeviceAttr() }) { required(it.info) }

    private suspend fun productInfo() =
        safeApiCall({ api.getProductInfo() }) { required(it.info) }

    private fun <T> required(value: T?): T = value ?: throw JsonParseException("Missing info")
}
