package com.densmac.dashcam.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.densmac.dashcam.data.db.DownloadEntity
import com.densmac.dashcam.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE remotePath = :remotePath LIMIT 1")
    suspend fun getByRemotePath(remotePath: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, updatedAt = :updatedAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateProgress(id: String, status: DownloadStatus, bytesDownloaded: Long, totalBytes: Long?, updatedAt: Long, errorMessage: String?)

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, updatedAt = :updatedAt, errorMessage = :errorMessage WHERE id = :id AND status != 'CANCELLED'")
    suspend fun updateProgressUnlessCancelled(id: String, status: DownloadStatus, bytesDownloaded: Long, totalBytes: Long?, updatedAt: Long, errorMessage: String?): Int

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, updatedAt: Long, errorMessage: String?)

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt, errorMessage = :errorMessage WHERE id = :id AND status != 'CANCELLED'")
    suspend fun updateStatusUnlessCancelled(id: String, status: DownloadStatus, updatedAt: Long, errorMessage: String?): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
