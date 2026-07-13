package com.densmac.dashcam.domain.repository

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder

interface FileRepository {
    suspend fun getFiles(folder: DashcamFolder, start: Int, end: Int): AppResult<List<DashcamFile>>
    suspend fun getBundles(folder: DashcamFolder): AppResult<List<DashcamFileBundle>>
    suspend fun takeSnapshot(): AppResult<Unit>
    suspend fun deleteFile(path: String): AppResult<Unit>
    fun thumbnailUrl(path: String): String
}
