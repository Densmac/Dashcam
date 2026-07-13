package com.densmac.dashcam.domain.usecase

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamFile
import com.densmac.dashcam.domain.model.DashcamFileBundle
import com.densmac.dashcam.domain.model.DashcamFolder
import com.densmac.dashcam.domain.model.DashcamMediaType
import com.densmac.dashcam.domain.repository.FileRepository
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

class GetFileBundlesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(folder: DashcamFolder): AppResult<List<DashcamFileBundle>> {
        val range = folder.defaultRange()
        return when (val result = fileRepository.getFiles(folder, range.first, range.last)) {
            is AppResult.Success -> AppResult.Success(buildBundles(result.data))
            is AppResult.Failure -> result
        }
    }

    fun buildBundles(files: List<DashcamFile>): List<DashcamFileBundle> {
        val all = mutableListOf<DashcamFileBundle>()
        files.groupBy { it.mediaType }.forEach { (mediaType, mediaFiles) ->
            val front = mediaFiles.filter { it.camera == DashcamCamera.FRONT }.sortedByDescending { it.cameraLocalDateTime }
            val rear = mediaFiles.filter { it.camera == DashcamCamera.REAR }.sortedByDescending { it.cameraLocalDateTime }.toMutableList()
            val unknown = mediaFiles.filter { it.camera == null }

            front.forEach { frontFile ->
                val rearMatch = nearestRear(frontFile.cameraLocalDateTime, rear)
                if (rearMatch != null) rear.remove(rearMatch)
                all += bundle(frontFile.folder, mediaType, frontFile, rearMatch)
            }
            rear.forEach { all += bundle(it.folder, mediaType, null, it) }
            unknown.forEach { all += bundle(it.folder, mediaType, it, null) }
        }
        return all.sortedWith(
            compareByDescending<DashcamFileBundle> { it.startTime ?: LocalDateTime.MIN }
                .thenBy { it.id }
        )
    }

    private fun nearestRear(time: LocalDateTime?, candidates: List<DashcamFile>): DashcamFile? {
        if (time == null) return null
        return candidates
            .mapNotNull { rear ->
                val rearTime = rear.cameraLocalDateTime ?: return@mapNotNull null
                rear to kotlin.math.abs(Duration.between(time, rearTime).seconds)
            }
            .filter { it.second <= DashcamConstants.FILE_PAIR_TOLERANCE_SECONDS }
            .minByOrNull { it.second }
            ?.first
    }

    private fun bundle(
        folder: DashcamFolder,
        mediaType: DashcamMediaType,
        front: DashcamFile?,
        rear: DashcamFile?
    ): DashcamFileBundle {
        val startTime = listOfNotNull(front?.cameraLocalDateTime, rear?.cameraLocalDateTime).minOrNull()
        val key = startTime?.let {
            "%04d%02d%02d%02d%02d%02d".format(
                it.year,
                it.monthValue,
                it.dayOfMonth,
                it.hour,
                it.minute,
                it.second
            )
        } ?: (front?.filename ?: rear?.filename ?: "unknown")
        return DashcamFileBundle(
            id = "${folder.apiValue}_${key}_${mediaType.name.lowercase()}",
            folder = folder,
            mediaType = mediaType,
            front = front,
            rear = rear,
            startTime = startTime,
            totalSizeKb = (front?.sizeKb ?: 0L) + (rear?.sizeKb ?: 0L),
            isCompletePair = front != null && rear != null
        )
    }
}

fun DashcamFolder.defaultRange(): IntRange = when (this) {
    DashcamFolder.LOOP -> 0..199
    DashcamFolder.EVENT,
    DashcamFolder.PARK,
    DashcamFolder.EMR,
    DashcamFolder.RACE -> 0..99
}
