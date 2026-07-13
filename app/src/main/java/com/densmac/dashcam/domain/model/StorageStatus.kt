package com.densmac.dashcam.domain.model

data class StorageStatus(
    val statusRaw: Int,
    val freeMb: Long,
    val totalMb: Long,
    val usedPercent: Float
) {
    val isAvailable: Boolean get() = statusRaw == 0 && totalMb > 0
    val usedMb: Long get() = (totalMb - freeMb).coerceAtLeast(0)
}
