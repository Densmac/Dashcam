package com.densmac.dashcam.data.repository

import android.content.Context
import android.os.Environment
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.data.db.DownloadEntity
import com.densmac.dashcam.data.db.dao.DownloadDao
import com.densmac.dashcam.data.download.DashcamDownloadManager
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DashcamMediaType
import com.densmac.dashcam.domain.model.DownloadItem
import com.densmac.dashcam.domain.model.DownloadStatus
import com.densmac.dashcam.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val downloadManager: DashcamDownloadManager,
    private val dispatchers: DispatchersProvider
) : DownloadRepository {
    override fun observeDownloads(): Flow<List<DownloadItem>> =
        downloadDao.observeDownloads().map { rows -> rows.map { it.toDomain() } }

    override suspend fun enqueueFileDownload(file: DashcamFile): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            val id = stableId(file.path)
            val existing = downloadDao.getById(id)
            if (existing?.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.COMPLETED)) {
                return@withContext AppResult.Success(Unit)
            }
            val localPath = localPathFor(file)
            val now = System.currentTimeMillis()
            downloadDao.upsert(
                DownloadEntity(
                    id = id,
                    remotePath = file.path,
                    localPath = localPath,
                    folder = file.folder.name,
                    camera = file.camera?.name,
                    mediaType = file.mediaType.name,
                    status = DownloadStatus.QUEUED,
                    bytesDownloaded = 0,
                    totalBytes = null,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    errorMessage = null
                )
            )
            downloadManager.enqueue(id, file.path, localPath)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.DownloadFailed)
        }
    }

    override suspend fun enqueueBundleDownload(bundle: DashcamFileBundle): AppResult<Unit> {
        bundle.front?.let {
            val result = enqueueFileDownload(it)
            if (result is AppResult.Failure) return result
        }
        bundle.rear?.let {
            val result = enqueueFileDownload(it)
            if (result is AppResult.Failure) return result
        }
        return AppResult.Success(Unit)
    }

    override suspend fun retryDownload(downloadId: String): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            val entity = downloadDao.getById(downloadId) ?: return@withContext AppResult.Failure(AppError.DownloadFailed)
            downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED, System.currentTimeMillis(), null)
            downloadManager.replace(downloadId, entity.remotePath, entity.localPath)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.DownloadFailed)
        }
    }

    override suspend fun cancelDownload(downloadId: String): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, System.currentTimeMillis(), null)
            downloadManager.cancel(downloadId)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.DownloadFailed)
        }
    }

    override suspend fun deleteLocalDownload(downloadId: String): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val entity = downloadDao.getById(downloadId) ?: return@runCatching
            if (entity.status in setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED)) {
                downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED, System.currentTimeMillis(), null)
            }
            downloadManager.cancelAndWait(downloadId)
            val finalDeleted = deleteIfPresent(File(entity.localPath))
            val partialDeleted = deleteIfPresent(File("${entity.localPath}.partial"))
            if (!finalDeleted || !partialDeleted) error("Could not delete local download")
            downloadDao.delete(downloadId)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(AppError.DownloadFailed) }
        )
    }

    private fun localPathFor(file: DashcamFile): String {
        val root = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Dashcam")
        val folder = when (file.folder) {
            DashcamFolder.LOOP -> "Recordings"
            DashcamFolder.EVENT -> "Snapshots"
            else -> file.folder.displayName
        }
        val suffix = when (file.camera) {
            DashcamCamera.FRONT -> "front"
            DashcamCamera.REAR -> "rear"
            null -> "unknown"
        }
        val extension = file.filename.substringAfterLast('.', "")
        val base = file.filename.substringBeforeLast('.')
            .replace("_f", "_front")
            .replace("_b", "_rear")
            .replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        val name = if (base.contains(suffix)) "$base.$extension" else "${base}_$suffix.$extension"
        return File(File(root, folder), name).absolutePath
    }

    private fun deleteIfPresent(file: File): Boolean = !file.exists() || file.delete()

    private fun stableId(remotePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(remotePath.toByteArray())
        return digest.joinToString("") { "%02x".format(Locale.US, it) }.take(24)
    }
}

private fun DownloadEntity.toDomain(): DownloadItem = DownloadItem(
    id = id,
    remotePath = remotePath,
    localPath = localPath,
    folder = DashcamFolder.valueOf(folder),
    camera = camera?.let { DashcamCamera.valueOf(it) },
    mediaType = DashcamMediaType.valueOf(mediaType),
    status = status,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    errorMessage = errorMessage
)
