package com.densmac.dashcam.data.api

import kotlinx.serialization.Serializable

@Serializable
data class DashcamEnvelope<T>(
    val result: Int,
    val info: T? = null
)

interface DashcamApiResponse {
    val result: Int
    fun infoText(): String?
}

data class DeviceAttrDto(
    val uuid: String = "",
    val softver: String = "",
    val hwver: String = "",
    val ssid: String = "",
    val bssid: String = "",
    val camnum: Int = 0,
    val curcamid: Int = 0
)

data class ProductInfoDto(
    val model: String = "",
    val company: String = "",
    val soc: String = "",
    val sp: String = ""
)

data class CapabilityDto(val value: String = "")
data class MediaInfoDto(val rtsp: String = "", val transport: String = "", val port: Int = 0)
data class SdInfoDto(val status: Int = -1, val free: Long = 0, val total: Long = 0)
data class RecDurationDto(val duration: Int = 0)
data class BatteryInfoDto(val capacity: Int = 0, val charge: Int = 0)
data class ParamItemDto(val name: String = "", val items: List<String> = emptyList(), val index: List<Int> = emptyList())
data class ParamValueDto(val name: String = "", val value: Int = 0)
data class SingleParamValueDto(val value: Int = 0)
data class FileItemDto(
    val name: String = "",
    val duration: Int = -1,
    val size: Long = 0,
    val createtime: Long = 0,
    val createtimestr: String? = null,
    val type: Int = 0
)
data class FileListGroupDto(
    val folder: String = "",
    val count: Int = 0,
    val files: List<FileItemDto>? = emptyList()
)

data class DeviceAttrResponse(override val result: Int = -1, val info: DeviceAttrDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class ProductInfoResponse(override val result: Int = -1, val info: ProductInfoDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class CapabilityResponse(override val result: Int = -1, val info: CapabilityDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.value
}

data class MediaInfoResponse(override val result: Int = -1, val info: MediaInfoDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class StringInfoResponse(override val result: Int = -1, val info: String? = null) : DashcamApiResponse {
    override fun infoText() = info
}

data class ParamItemsResponse(override val result: Int = -1, val info: List<ParamItemDto>? = null) : DashcamApiResponse {
    override fun infoText() = info?.joinToString()
}

data class ParamValuesResponse(override val result: Int = -1, val info: List<ParamValueDto>? = null) : DashcamApiResponse {
    override fun infoText() = info?.joinToString()
}

data class ParamValueResponse(override val result: Int = -1, val info: SingleParamValueDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class SdInfoResponse(override val result: Int = -1, val info: SdInfoDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class RecDurationResponse(override val result: Int = -1, val info: RecDurationDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class BatteryInfoResponse(override val result: Int = -1, val info: BatteryInfoDto? = null) : DashcamApiResponse {
    override fun infoText() = info?.toString()
}

data class FileListResponse(override val result: Int = -1, val info: List<FileListGroupDto>? = null) : DashcamApiResponse {
    override fun infoText() = info?.joinToString()
}
