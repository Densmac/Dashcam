package com.densmac.dashcam.domain.repository

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DownloadItem
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>
    suspend fun enqueueFileDownload(file: DashcamFile): AppResult<Unit>
    suspend fun enqueueBundleDownload(bundle: DashcamFileBundle): AppResult<Unit>
    suspend fun retryDownload(downloadId: String): AppResult<Unit>
    suspend fun cancelDownload(downloadId: String): AppResult<Unit>
    suspend fun deleteLocalDownload(downloadId: String): AppResult<Unit>
}
