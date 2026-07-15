package com.densmac.dashcam.data.api

import com.densmac.dashcam.core.common.DashcamConstants
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface DashcamApi {
    @GET(DashcamConstants.ENDPOINT_GET_DEVICE_ATTR)
    suspend fun getDeviceAttr(): DeviceAttrResponse

    @GET(DashcamConstants.ENDPOINT_GET_PRODUCT_INFO)
    suspend fun getProductInfo(): ProductInfoResponse

    @GET(DashcamConstants.ENDPOINT_CAPABILITY)
    suspend fun getCapability(): CapabilityResponse

    @GET(DashcamConstants.ENDPOINT_GET_MEDIA_INFO)
    suspend fun getMediaInfo(): MediaInfoResponse

    @GET(DashcamConstants.ENDPOINT_PLAYBACK)
    suspend fun playback(@Query("param") param: String): StringInfoResponse

    @GET(DashcamConstants.ENDPOINT_SETTING)
    suspend fun setting(@Query("param") param: String): StringInfoResponse

    @GET(DashcamConstants.ENDPOINT_ENTER_RECORDER)
    suspend fun enterRecorder(): StringInfoResponse

    @GET(DashcamConstants.ENDPOINT_GET_PARAM_ITEMS)
    suspend fun getParamItems(@Query("param") param: String = "all"): ParamItemsResponse

    @GET(DashcamConstants.ENDPOINT_GET_PARAM_VALUE)
    suspend fun getParamValues(@Query("param") param: String = "all"): ParamValuesResponse

    @GET(DashcamConstants.ENDPOINT_GET_PARAM_VALUE)
    suspend fun getParamValue(@Query("param") param: String): ParamValueResponse

    @GET(DashcamConstants.ENDPOINT_SET_PARAM_VALUE)
    suspend fun setParamValue(
        @Query("param") param: String,
        @Query("value") value: Int
    ): StringInfoResponse

    @GET(DashcamConstants.ENDPOINT_GET_SD_INFO)
    suspend fun getSdInfo(): SdInfoResponse

    @GET(DashcamConstants.ENDPOINT_GET_REC_DURATION)
    suspend fun getRecDuration(): RecDurationResponse

    @GET(DashcamConstants.ENDPOINT_GET_BATTERY_INFO)
    suspend fun getBatteryInfo(): BatteryInfoResponse

    @GET(DashcamConstants.ENDPOINT_SNAPSHOT)
    suspend fun snapshot(): StringInfoResponse

    @GET(DashcamConstants.ENDPOINT_GET_FILE_LIST)
    suspend fun getFileList(
        @Query("folder") folder: String,
        @Query("start") start: Int,
        @Query("end") end: Int
    ): FileListResponse

    @GET(DashcamConstants.ENDPOINT_GET_THUMBNAIL)
    suspend fun getThumbnail(@Query("file", encoded = true) fullPath: String): ResponseBody

    @GET(DashcamConstants.ENDPOINT_DELETE_FILE)
    suspend fun deleteFile(@Query("file", encoded = true) fullPath: String): StringInfoResponse
}
