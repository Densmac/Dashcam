package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.domain.repository.FileRepository
import javax.inject.Inject

class TakeSnapshotUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke() = fileRepository.takeSnapshot()
}
