package com.densmac.dashcam.domain.model

import java.time.LocalDateTime

data class DashcamFileBundle(
    val id: String,
    val folder: DashcamFolder,
    val mediaType: DashcamMediaType,
    val front: DashcamFile?,
    val rear: DashcamFile?,
    val startTime: LocalDateTime?,
    val totalSizeKb: Long,
    val isCompletePair: Boolean
) {
    val primary: DashcamFile? get() = front ?: rear
}
