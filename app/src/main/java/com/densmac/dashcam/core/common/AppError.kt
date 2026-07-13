package com.densmac.dashcam.core.common

sealed interface AppError {
    data object NotConnectedToDashcam : AppError
    data object DashcamApiUnreachable : AppError
    data object UnsupportedEndpoint : AppError
    data object SdCardUnavailable : AppError
    data object RtspUnavailable : AppError
    data object DownloadFailed : AppError
    data object DeleteFailed : AppError
    data object PermissionDenied : AppError
    data object OperationCancelled : AppError
    data class HttpError(val code: Int, val body: String?) : AppError
    data class ApiError(val result: Int, val info: String?) : AppError
    data class ParseError(val raw: String?) : AppError
    data class Unknown(val throwable: Throwable) : AppError
}

fun AppError.userMessage(): String = when (this) {
    AppError.NotConnectedToDashcam -> "Connect to DASHCAM Wi-Fi, then try again."
    AppError.DashcamApiUnreachable -> "The app could not reach the dashcam. Make sure the camera is powered on and your phone is connected to DASHCAM Wi-Fi."
    AppError.UnsupportedEndpoint -> "This feature is not supported by this dashcam."
    AppError.SdCardUnavailable -> "The dashcam SD card is unavailable."
    AppError.RtspUnavailable -> "Live preview could not start. Re-entering recorder mode may fix it."
    AppError.DownloadFailed -> "Download failed. Keep your phone connected to DASHCAM Wi-Fi and try again."
    AppError.DeleteFailed -> "Delete failed. The file may already be gone or the dashcam is busy."
    AppError.PermissionDenied -> "Permission was denied."
    AppError.OperationCancelled -> "Operation cancelled."
    is AppError.HttpError -> "Dashcam HTTP error ${code}."
    is AppError.ApiError -> "Dashcam returned result ${result}."
    is AppError.ParseError -> "The dashcam response could not be read."
    is AppError.Unknown -> "Something went wrong."
}

enum class AppNotice {
    SnapshotSaved,
    SnapshotFailed,
    DownloadQueued,
    Updated,
    DeletedFromDashcam,
    DeletePartiallyFailed
}

fun AppNotice.userMessage(): String = when (this) {
    AppNotice.SnapshotSaved -> "Snapshot saved on dashcam."
    AppNotice.SnapshotFailed -> "Snapshot failed. Check dashcam connection and try again."
    AppNotice.DownloadQueued -> "Download queued."
    AppNotice.Updated -> "Updated."
    AppNotice.DeletedFromDashcam -> "Deleted from dashcam."
    AppNotice.DeletePartiallyFailed -> "Delete partially failed."
}

fun recordingUserMessage(enabled: Boolean): String =
    "Recording ${if (enabled) "started" else "stopped"}."

fun deleteFilesUserMessage(deletedCount: Int, failedCount: Int): String = when {
    failedCount == 0 -> "Deleted $deletedCount file${if (deletedCount == 1) "" else "s"}."
    deletedCount > 0 -> "Deleted $deletedCount; $failedCount failed."
    else -> AppError.DeleteFailed.userMessage()
}
