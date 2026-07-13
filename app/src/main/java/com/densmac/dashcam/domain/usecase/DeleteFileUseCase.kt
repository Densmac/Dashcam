package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.repository.FileRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

class DeleteFileUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(files: List<DashcamFile>): DeleteResult {
        val deleted = mutableListOf<DashcamFile>()
        val failed = mutableListOf<Pair<DashcamFile, com.densmac.dashcam.core.common.AppError>>()
        for (file in files) {
            when (val result = fileRepository.deleteFile(file.path)) {
                is AppResult.Success -> deleted += file
                is AppResult.Failure -> failed += file to result.error
            }
            delay(150)
        }
        return DeleteResult(deleted = deleted, failed = failed)
    }
}

data class DeleteResult(
    val deleted: List<DashcamFile>,
    val failed: List<Pair<DashcamFile, com.densmac.dashcam.core.common.AppError>>
)
