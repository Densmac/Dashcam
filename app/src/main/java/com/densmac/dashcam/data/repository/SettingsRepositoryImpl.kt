package com.densmac.dashcam.data.repository

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.api.ParamItemDto
import com.densmac.dashcam.data.api.ParamValueDto
import com.densmac.dashcam.data.api.safeApiCall
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamSettings
import com.densmac.dashcam.domain.model.LevelSetting
import com.densmac.dashcam.domain.model.LoopDuration
import com.densmac.dashcam.domain.model.TimelapseRate
import com.densmac.dashcam.domain.repository.SettingsRepository
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val api: DashcamApi,
    private val networkBinder: DashcamNetworkBinder
) : SettingsRepository {
    private val safeParams = setOf(
        "mic",
        "osd",
        "speaker",
        "gsr_sensitivity",
        "rec_split_duration",
        "rec",
        "timelapse_rate",
        "switchcam"
    )

    override suspend fun getSettings(): AppResult<DashcamSettings> {
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        val enter = safeApiCall({ api.setting("enter") }) { Unit }
        if (enter is AppResult.Failure) return enter
        val itemsResult = safeApiCall({ api.getParamItems() }) { it.info.orEmpty() }
        if (itemsResult is AppResult.Failure) return itemsResult
        val valuesResult = safeApiCall({ api.getParamValues() }) { it.info.orEmpty() }
        if (valuesResult is AppResult.Failure) return valuesResult
        return AppResult.Success(
            mapSettings(
                (itemsResult as AppResult.Success).data,
                (valuesResult as AppResult.Success).data
            )
        )
    }

    override suspend fun setMic(enabled: Boolean) = setAndConfirm("mic", if (enabled) 1 else 0)
    override suspend fun setOsd(enabled: Boolean) = setAndConfirm("osd", if (enabled) 1 else 0)
    override suspend fun setRecording(enabled: Boolean) = setAndConfirm("rec", if (enabled) 1 else 0)
    override suspend fun setLoopDuration(duration: LoopDuration) = setAndConfirm("rec_split_duration", duration.value)
    override suspend fun setSpeaker(level: LevelSetting) = setAndConfirm("speaker", level.value)
    override suspend fun setGSensor(level: LevelSetting) = setAndConfirm("gsr_sensitivity", level.value)
    override suspend fun setTimelapse(rate: TimelapseRate) = setAndConfirm("timelapse_rate", rate.value)
    override suspend fun switchCamera(camera: DashcamCamera, swappedMapping: Boolean) =
        setAndConfirm("switchcam", camera.switchParamValue(swappedMapping))

    private suspend fun setAndConfirm(param: String, value: Int): AppResult<Unit> {
        if (param !in safeParams) return AppResult.Failure(com.densmac.dashcam.core.common.AppError.UnsupportedEndpoint)
        val bind = ensureBound()
        if (bind is AppResult.Failure) return bind
        val enter = safeApiCall({ api.setting("enter") }) { Unit }
        if (enter is AppResult.Failure) return enter
        return safeApiCall({ api.setParamValue(param, value) }) { Unit }
    }

    private suspend fun ensureBound(): AppResult<Unit> =
        when (val bind = networkBinder.findAndBindDashcamNetwork()) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> bind
        }

    private fun mapSettings(items: List<ParamItemDto>, values: List<ParamValueDto>): DashcamSettings {
        val supported = items.associateBy { it.name }
        val current = values.associate { it.name to it.value }
        fun present(name: String) = supported.containsKey(name) && current.containsKey(name)
        return DashcamSettings(
            micEnabled = if (present("mic")) current["mic"] == 1 else null,
            osdEnabled = if (present("osd")) current["osd"] == 1 else null,
            recordingEnabled = current["rec"]?.let { it == 1 },
            loopDuration = if (present("rec_split_duration")) LoopDuration.fromValue(current["rec_split_duration"]) else null,
            speakerLevel = if (present("speaker")) LevelSetting.fromValue(current["speaker"]) else null,
            gSensorSensitivity = if (present("gsr_sensitivity")) LevelSetting.fromValue(current["gsr_sensitivity"]) else null,
            timelapseRate = if (present("timelapse_rate")) TimelapseRate.fromValue(current["timelapse_rate"]) else null,
            resolutionLabel = supported["rec_resolution"]?.items?.firstOrNull()
        )
    }
}
