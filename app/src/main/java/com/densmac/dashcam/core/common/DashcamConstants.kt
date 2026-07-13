package com.densmac.dashcam.core.common

object DashcamConstants {
    const val DEFAULT_HOST = "192.168.169.1"
    const val HTTP_BASE_URL = "http://192.168.169.1/"
    const val RTSP_TRACK2_URL = "rtsp://192.168.169.1:554/track2"
    const val RTSP_ROOT_URL = "rtsp://192.168.169.1:554"
    const val DASHCAM_SSID = "DASHCAM"

    const val ENDPOINT_GET_DEVICE_ATTR = "app/getdeviceattr"
    const val ENDPOINT_GET_PRODUCT_INFO = "app/getproductinfo"
    const val ENDPOINT_CAPABILITY = "app/capability"
    const val ENDPOINT_GET_MEDIA_INFO = "app/getmediainfo"
    const val ENDPOINT_PLAYBACK = "app/playback"
    const val ENDPOINT_SETTING = "app/setting"
    const val ENDPOINT_ENTER_RECORDER = "app/enterrecorder"
    const val ENDPOINT_GET_PARAM_ITEMS = "app/getparamitems"
    const val ENDPOINT_GET_PARAM_VALUE = "app/getparamvalue"
    const val ENDPOINT_SET_PARAM_VALUE = "app/setparamvalue"
    const val ENDPOINT_GET_SD_INFO = "app/getsdinfo"
    const val ENDPOINT_GET_REC_DURATION = "app/getrecduration"
    const val ENDPOINT_GET_BATTERY_INFO = "app/getbatteryinfo"
    const val ENDPOINT_SNAPSHOT = "app/snapshot"
    const val ENDPOINT_GET_FILE_LIST = "app/getfilelist"
    const val ENDPOINT_GET_THUMBNAIL = "app/getthumbnail"
    const val ENDPOINT_DELETE_FILE = "app/deletefile"

    const val HTTP_TIMEOUT_SECONDS = 8L
    const val DOWNLOAD_TIMEOUT_SECONDS = 30L
    const val STREAM_START_TIMEOUT_MS = 5_000L
    const val DEVICE_POLL_INTERVAL_MS = 2_500L
    const val LIVE_RECONNECT_DELAY_MS = 1_500L
    const val FILE_PAIR_TOLERANCE_SECONDS = 3L
}
