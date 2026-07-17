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
import com.densmac.dashcam.core.common.Logger
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.core.network.DashcamNetworkBinder
import com.densmac.dashcam.core.network.DownloadOkHttpClient
import com.densmac.dashcam.data.api.DashcamApi
import com.densmac.dashcam.data.api.safeApiCall
import com.densmac.dashcam.data.db.dao.DownloadDao
import com.densmac.dashcam.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    @DownloadOkHttpClient private val client: OkHttpClient,
    private val api: DashcamApi,
    private val downloadDao: DownloadDao,
    private val networkBinder: DashcamNetworkBinder,
    private val notificationHelper: DownloadNotificationHelper,
    private val coordinator: DownloadCoordinator,
    private val dispatchers: DispatchersProvider
) : CoroutineWorker(context, params) {
    object Keys {
        const val DOWNLOAD_ID = "download_id"
        const val REMOTE_PATH = "remote_path"
        const val LOCAL_PATH = "local_path"
    }

    private sealed interface TransferOutcome {
        data object Completed : TransferOutcome
        data object Cancelled : TransferOutcome
        data class HttpError(val message: String) : TransferOutcome
    }

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val id = inputData.getString(Keys.DOWNLOAD_ID) ?: return@withContext Result.failure()
        val remotePath = inputData.getString(Keys.REMOTE_PATH) ?: return@withContext Result.failure()
        val localPath = inputData.getString(Keys.LOCAL_PATH) ?: return@withContext Result.failure()

        val finalFile = File(localPath)
        val partialFile = File("$localPath.partial")

        // Stop retrying forever: after enough WorkManager attempts, surface a clean Failed state.
        if (runAttemptCount >= MAX_WORK_ATTEMPTS) {
            Logger.d("DownloadWorker giving up after $runAttemptCount attempts: id=$id")
            markFailedUnlessCancelled(id, AppError.DownloadFailed.userMessage())
            return@withContext Result.failure()
        }

        try {
            Logger.d("DownloadWorker starting: id=$id remote=$remotePath local=$localPath attempt=$runAttemptCount")
            finalFile.parentFile?.mkdirs()

            if (downloadDao.updateStatusUnlessCancelled(id, DownloadStatus.RUNNING, System.currentTimeMillis(), null) == 0) {
                return@withContext Result.failure()
            }
            setForeground(notificationHelper.foregroundInfo("Downloading dashcam file", 0, true))

            val bind = networkBinder.findAndBindDashcamNetwork()
            if (bind is AppResult.Failure) {
                Logger.d("DownloadWorker bind failed: ${bind.error}")
                downloadDao.updateStatusUnlessCancelled(id, DownloadStatus.FAILED, System.currentTimeMillis(), bind.error.userMessage())
                return@withContext Result.retry()
            }
            Logger.d("DownloadWorker bound to dashcam network")

            // Serialize the transfer: the camera is single-session, so only one download runs at
            // a time. Others queue here instead of contending and stalling each other.
            coordinator.withTransferSlot {
                val playback = safeApiCall({ api.playback("enter") }) { Unit }
                if (playback is AppResult.Failure) {
                    Logger.d("DownloadWorker playback enter failed: ${playback.error}")
                    downloadDao.updateStatusUnlessCancelled(id, DownloadStatus.FAILED, System.currentTimeMillis(), playback.error.userMessage())
                    return@withTransferSlot Result.retry()
                }

                val url = DashcamConstants.HTTP_BASE_URL + remotePath.removePrefix("/")
                Logger.d("DownloadWorker requesting: $url")

                // In-run retries: a transient stall resumes from the .partial file instead of
                // bouncing out to WorkManager's backoff, so an alive-but-slow camera keeps going.
                var readAttempt = 0
                while (true) {
                    val outcome = runCatching { transferOnce(id, url, partialFile) }.getOrElse { throwable ->
                        if (throwable is CancellationException) throw throwable
                        if (throwable !is IOException) throw throwable
                        null // transient network stall
                    }
                    when (outcome) {
                        TransferOutcome.Completed -> return@withTransferSlot finalize(id, partialFile, finalFile)
                        TransferOutcome.Cancelled -> {
                            markCancelledIfExplicit(id)
                            return@withTransferSlot Result.failure()
                        }
                        is TransferOutcome.HttpError -> {
                            markFailedUnlessCancelled(id, outcome.message)
                            return@withTransferSlot Result.retry()
                        }
                        null -> {
                            readAttempt++
                            if (readAttempt > MAX_READ_RETRIES) {
                                throw IOException("Download stalled after $MAX_READ_RETRIES in-run retries")
                            }
                            if (shouldStop(id)) {
                                markCancelledIfExplicit(id)
                                return@withTransferSlot Result.failure()
                            }
                            Logger.d("DownloadWorker transient stall; resume attempt $readAttempt for id=$id")
                            delay(READ_RETRY_DELAY_MS)
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.failure()
            }
        } catch (cancellation: CancellationException) {
            markCancelledIfExplicit(id)
            throw cancellation
        } catch (throwable: Throwable) {
            Logger.e("DownloadWorker failed: id=$id remote=$remotePath", throwable)
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

    /**
     * One transfer pass. Resumes from [partialFile] via a Range request, streams to it, and
     * throws [IOException] on a network stall so the caller can retry-resume. HTTP errors and
     * cancellation return a typed outcome instead.
     */
    private suspend fun transferOnce(id: String, url: String, partialFile: File): TransferOutcome {
        var existing = partialFile.takeIf { it.exists() }?.length() ?: 0L
        var request = Request.Builder().url(url).apply {
            if (existing > 0) header("Range", "bytes=$existing-")
        }.build()
        var response = client.newCall(request).execute()
        Logger.d("DownloadWorker response: code=${response.code} contentLength=${response.body?.contentLength()}")
        if (existing > 0 && response.code == 200) {
            // Server ignored the Range and is sending the whole file: reset and start clean.
            response.close()
            if (partialFile.exists() && !partialFile.delete()) error("Could not reset partial download")
            existing = 0L
            request = Request.Builder().url(url).build()
            response = client.newCall(request).execute()
        }
        if (!response.isSuccessful) {
            response.close()
            return TransferOutcome.HttpError(AppError.HttpError(response.code, null).userMessage())
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
                        if (shouldStop(id)) return TransferOutcome.Cancelled
                        // Yield the camera to a live stream: pause reads (and show Paused) while one
                        // is playing, then resume where we left off when it stops.
                        if (coordinator.streaming.value) {
                            downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.PAUSED, downloaded, totalBytes, System.currentTimeMillis(), null)
                            coordinator.awaitStreamingIdle()
                            if (shouldStop(id)) return TransferOutcome.Cancelled
                            downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.RUNNING, downloaded, totalBytes, System.currentTimeMillis(), null)
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded >= nextUpdate) {
                            val progress = totalBytes?.let { ((downloaded * 100) / it).toInt() } ?: 0
                            setForeground(notificationHelper.foregroundInfo("Downloading dashcam file", progress, totalBytes == null))
                            if (downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.RUNNING, downloaded, totalBytes, System.currentTimeMillis(), null) == 0) {
                                return TransferOutcome.Cancelled
                            }
                            nextUpdate = downloaded + 512 * 1024
                        }
                    }
                    output.flush()
                }
            }
        }
        return TransferOutcome.Completed
    }

    private suspend fun finalize(id: String, partialFile: File, finalFile: File): Result {
        if (shouldStop(id)) {
            markCancelledIfExplicit(id)
            return Result.failure()
        }
        if (finalFile.exists() && !finalFile.delete()) error("Could not replace existing download")
        if (!partialFile.renameTo(finalFile)) error("Could not finalize download")
        val bytes = finalFile.length()
        Logger.d("DownloadWorker completed: id=$id bytes=$bytes path=${finalFile.absolutePath}")
        return if (downloadDao.updateProgressUnlessCancelled(id, DownloadStatus.COMPLETED, bytes, bytes, System.currentTimeMillis(), null) == 0) {
            Result.failure()
        } else {
            Result.success()
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
        private const val MAX_READ_RETRIES = 4
        private const val READ_RETRY_DELAY_MS = 1_500L
        private const val MAX_WORK_ATTEMPTS = 8

        fun data(id: String, remotePath: String, localPath: String): Data =
            Data.Builder()
                .putString(Keys.DOWNLOAD_ID, id)
                .putString(Keys.REMOTE_PATH, remotePath)
                .putString(Keys.LOCAL_PATH, localPath)
                .build()
    }
}
