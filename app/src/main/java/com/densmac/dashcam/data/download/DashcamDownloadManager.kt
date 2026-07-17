package com.densmac.dashcam.data.download

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.densmac.dashcam.core.common.Logger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashcamDownloadManager @Inject constructor(
    private val workManager: WorkManager
) {
    // Quick linear backoff (vs WorkManager's 30s exponential default) so a retry resumes soon.
    private fun buildRequest(id: String, remotePath: String, localPath: String) =
        OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadWorker.data(id, remotePath, localPath))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()

    fun enqueue(id: String, remotePath: String, localPath: String) {
        Logger.d("Enqueue download work: id=$id remote=$remotePath local=$localPath")
        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, buildRequest(id, remotePath, localPath))
    }

    fun replace(id: String, remotePath: String, localPath: String) {
        Logger.d("Replace download work: id=$id remote=$remotePath local=$localPath")
        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, buildRequest(id, remotePath, localPath))
    }

    fun cancel(id: String) {
        cancelAndWait(id)
    }

    fun cancelAndWait(id: String) {
        workManager.cancelUniqueWork(id).result.get()
    }
}
