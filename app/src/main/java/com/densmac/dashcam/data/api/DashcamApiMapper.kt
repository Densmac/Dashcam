package com.densmac.dashcam.data.api

import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DateTimeFormatters
import com.densmac.dashcam.core.common.Logger
import com.densmac.dashcam.domain.model.BatteryInfo
import com.densmac.dashcam.domain.model.DashcamDevice
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DashcamMediaType
import com.densmac.dashcam.domain.model.StorageStatus
import com.densmac.dashcam.domain.usecase.DashcamFilenameParser
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import javax.inject.Inject

suspend fun <T : DashcamApiResponse, R> safeApiCall(
    block: suspend () -> T,
    map: (T) -> R
): AppResult<R> = try {
    val response = block()
    when (response.result) {
        0 -> AppResult.Success(map(response))
        98 -> AppResult.Failure(AppError.UnsupportedEndpoint)
        else -> AppResult.Failure(AppError.ApiError(response.result, response.infoText()))
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: SocketTimeoutException) {
    AppResult.Failure(AppError.DashcamApiUnreachable)
} catch (_: IOException) {
    AppResult.Failure(AppError.DashcamApiUnreachable)
} catch (http: HttpException) {
    AppResult.Failure(AppError.HttpError(http.code(), http.response()?.errorBody()?.string()))
} catch (parse: JsonParseException) {
    AppResult.Failure(AppError.ParseError(parse.message))
} catch (throwable: Throwable) {
    Logger.e("Unexpected dashcam API failure", throwable)
    AppResult.Failure(AppError.Unknown(throwable))
}

class DashcamApiMapper @Inject constructor(
    private val filenameParser: DashcamFilenameParser
) {
    fun device(attr: DeviceAttrDto, product: ProductInfoDto?): DashcamDevice = DashcamDevice(
        uuid = attr.uuid,
        model = product?.model,
        softwareVersion = attr.softver,
        hardwareVersion = attr.hwver,
        ssid = attr.ssid,
        cameraCount = attr.camnum,
        currentCameraId = attr.curcamid,
        soc = product?.soc
    )

    fun storage(dto: SdInfoDto): StorageStatus {
        val usedPercent = if (dto.total > 0) {
            ((dto.total - dto.free).toFloat() / dto.total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return StorageStatus(
            statusRaw = dto.status,
            freeMb = dto.free,
            totalMb = dto.total,
            usedPercent = usedPercent
        )
    }

    fun battery(dto: BatteryInfoDto?): BatteryInfo? = dto?.let {
        BatteryInfo(capacity = it.capacity, charging = it.charge > 0)
    }

    fun file(dto: FileItemDto, folder: DashcamFolder, thumbnailUrl: String): DashcamFile {
        val parsed = filenameParser.parse(dto.name)
        val mediaType = when {
            dto.type == 2 || parsed?.extension == "ts" -> DashcamMediaType.VIDEO
            dto.type == 1 || parsed?.extension in setOf("jpg", "jpeg") -> DashcamMediaType.PICTURE
            else -> DashcamMediaType.UNKNOWN
        }
        val cameraTime = parsed?.localDateTime ?: parseCameraTime(dto.createtimestr)
        return DashcamFile(
            path = dto.name,
            filename = dto.name.substringAfterLast('/'),
            folder = folder,
            camera = parsed?.camera,
            mediaType = mediaType,
            sizeKb = dto.size,
            cameraLocalDateTime = cameraTime,
            createTimeRaw = dto.createtime.takeIf { it > 0 },
            createTimeString = dto.createtimestr,
            durationSeconds = dto.duration.takeIf { it >= 0 },
            typeRaw = dto.type,
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun parseCameraTime(value: String?): LocalDateTime? = runCatching {
        if (value.isNullOrBlank()) null else LocalDateTime.parse(value, DateTimeFormatters.dashcamTimestamp)
    }.getOrNull()
}
