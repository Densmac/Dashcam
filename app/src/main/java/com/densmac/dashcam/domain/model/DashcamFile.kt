package com.densmac.dashcam.domain.model

import java.time.LocalDateTime

enum class DashcamMediaType {
    VIDEO,
    PICTURE,
    UNKNOWN
}

data class DashcamFile(
    val path: String,
    val filename: String,
    val folder: DashcamFolder,
    val camera: DashcamCamera?,
    val mediaType: DashcamMediaType,
    val sizeKb: Long,
    val cameraLocalDateTime: LocalDateTime?,
    val createTimeRaw: Long?,
    val createTimeString: String?,
    val durationSeconds: Int?,
    val typeRaw: Int,
    val thumbnailUrl: String?
)
