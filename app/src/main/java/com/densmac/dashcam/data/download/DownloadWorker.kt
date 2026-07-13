package com.densmac.dashcam.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.core.network.DownloadOkHttpClient
import com.densmac.dashcam.data.db.dao.DownloadDao
import com.densmac.dashcam.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    @DownloadOkHttpClient private val client: OkHttpClient,
    private val downloadDao: DownloadDao,
    private val networkBinder: DashcamNetworkBinder,
    private val notificationHelper: DownloadNotificationHelper,
    private val dispatchers: DispatchersProvider
) : CoroutineWorker(context, params) {
    object Keys {
        const val DOWNLOAD_ID = "download_id"
        const val REMOTE_PATH = "remote_path"
        const val LOCAL_PATH = "local_path"
    }

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val id = inputData.getString(Keys.DOWNLOAD_ID) ?: return@withContext Result.failure()
        val remotePath = inputData.getString(Keys.REMOTE_PATH) ?: return@withContext Result.failure()
        val localPath = inputData.getString(Keys.LOCAL_PATH) ?: return@withContext Result.failure()

        try {
            val finalFile = File(localPath)
            val partialFile = File("$localPath.partial")
            finalFile.parentFile?.mkdirs()

            if (downloadDao.updateStatusUnlessCancelled(id, DownloadStatus.RUNNING, System.currentTimeMillis(), null) == 0) {
                return@withContext Result.failure()
            }
            setForeground(notificationHelper.foregroundInfo("Downloading dashcam file", 0, true))

            val bind = networkBinder.findAndBindDashcamNetwork()
            if (bind is AppResult.Failure) {
                downloadDao.updateStatusUnlessCancelled(id, DownloadStatus.FAILED, System.currentTimeMillis(), bind.error.userMessage())
                return@withContext Result.retry()
            }
            val url = DashcamConstants.HTTP_BASE_URL + remotePath.removePrefix("/")
            var existing = partialFile.takeIf { it.exists() }?.length() ?: 0L
            var request = Request.Builder().url(url).apply {
                if (existing > 0) header("Range", "bytes=$existing-")
            }.build()
            var response = client.newCall(request).execute()
            if (existing > 0 && response.code == 200) {
                response.close()
                if (partialFile.exists() && !partialFile.delete()) error("Could not reset partial download")
                existing = 0L
                request = Request.Builder().url(url).build()
                response = client.newCall(request).execute()
            }
            if (!response.isSuccessful) {
                downloadDao.updateStatusUnlessCancelled(
                    id,
                    DownloadStatus.FAILED,
                    System.currentTimeMillis(),
                    AppError.HttpError(response.code, null).userMessage()
                )
                return@withContext Result.retry()
            }
            response.use { res ->
                val body = res.body ?: error("Empty body")
                val contentLength = body.contentLength().takeIf { it > 0 }
                val totalBytes = contentLength?.let { it + existing }
                var downloaded = existing
                body.byteStream().use { input ->
                    FileOutputStream(partialFile, existing > 0).buffered(128 * 1024).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var nextUpdate = downloaded + 512 * 1024
                        while (true) {
                            if (shouldStop(id)) {
                                markCancelledIfExplicit(id)
                                return@withContext Result.failure()
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (shouldStop(id)) {
                                markCancelledIfExplicit(id)
                                return@withContext Result.failure()
                            }
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded >= nextUpdate) {
                                val progress = totalBytes?.let { ((downloaded * 100) / it).toInt() } ?: 0
                                setForeground(notificationHelper.foregroundInfo("Downloading dashcam file", progress, totalBytes == null))
                                if (downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.RUNNING, downloaded, totalBytes, System.currentTimeMillis(), null) == 0) {
                                    return@withContext Result.failure()
                                }
                                nextUpdate = downloaded + 512 * 1024
                            }
                        }
                    }
                }
                if (shouldStop(id)) {
                    markCancelledIfExplicit(id)
                    return@withContext Result.failure()
                }
                if (finalFile.exists() && !finalFile.delete()) error("Could not replace existing download")
                if (!partialFile.renameTo(finalFile)) {
                    error("Could not finalize download")
                }
                if (shouldStop(id)) {
                    markCancelledIfExplicit(id)
                    return@withContext Result.failure()
                }
                if (downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.COMPLETED, downloaded, totalBytes, System.currentTimeMillis(), null) == 0) {
                    Result.failure()
                } else {
                    Result.success()
                }
            }
        } catch (cancellation: CancellationException) {
            markCancelledIfExplicit(id)
            throw cancellation
        } catch (throwable: Throwable) {
            when (statusOrNull(id)) {
                DownloadStatus.CANCELLED -> {
                    markCancelledIfExplicit(id)
                    Result.failure()
                }
                else -> {
                    if (isStopped) {
                        Result.failure()
                    } else {
                        markFailedUnlessCancelled(id, AppError.DownloadFailed.userMessage())
                        Result.retry()
                    }
                }
            }
        }
    }

    private suspend fun shouldStop(id: String): Boolean =
        isStopped || downloadDao.getById(id)?.status in setOf(null, DownloadStatus.CANCELLED)

    private suspend fun statusOrNull(id: String): DownloadStatus? =
        runCatching { downloadDao.getById(id)?.status }.getOrNull()

    private suspend fun markFailedUnlessCancelled(id: String, message: String) {
        runCatching {
            downloadDao.updateStatusUnlessCancelled(
                id,
                DownloadStatus.FAILED,
                System.currentTimeMillis(),
                message
            )
        }
    }

    private suspend fun markCancelledIfExplicit(id: String) {
        runCatching {
            if (downloadDao.getById(id)?.status == DownloadStatus.CANCELLED) {
                downloadDao.updateStatus(id, DownloadStatus.CANCELLED, System.currentTimeMillis(), null)
            }
        }
    }

    companion object {
        fun data(id: String, remotePath: String, localPath: String): Data =
            Data.Builder()
                .putString(Keys.DOWNLOAD_ID, id)
                .putString(Keys.REMOTE_PATH, remotePath)
                .putString(Keys.LOCAL_PATH, localPath)
                .build()
    }
}
