package com.densmac.dashcam.data.download

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashcamDownloadManager @Inject constructor(
    private val workManager: WorkManager
) {
    fun enqueue(id: String, remotePath: String, localPath: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadWorker.data(id, remotePath, localPath))
            .build()
        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.KEEP, request)
    }

    fun replace(id: String, remotePath: String, localPath: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(DownloadWorker.data(id, remotePath, localPath))
            .build()
        workManager.enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(id: String) {
        cancelAndWait(id)
    }

    fun cancelAndWait(id: String) {
        workManager.cancelUniqueWork(id).result.get()
    }
}
