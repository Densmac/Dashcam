package com.densmac.dashcam.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.densmac.dashcam.domain.model.DownloadStatus

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val remotePath: String,
    val localPath: String,
    val folder: String,
    val camera: String?,
    val mediaType: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?
)
