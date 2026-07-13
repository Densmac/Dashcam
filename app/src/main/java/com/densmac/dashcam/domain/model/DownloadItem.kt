package com.densmac.dashcam.domain.model

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String,
    val remotePath: String,
    val localPath: String,
    val folder: DashcamFolder,
    val camera: DashcamCamera?,
    val mediaType: DashcamMediaType,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?
) {
    val progress: Float
        get() = if (totalBytes != null && totalBytes > 0) {
            (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
