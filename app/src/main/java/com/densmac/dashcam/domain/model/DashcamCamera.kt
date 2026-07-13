package com.densmac.dashcam.domain.model

enum class DashcamCamera(val switchValue: Int, val suffix: String, val displayName: String) {
    FRONT(0, "f", "Front"),
    REAR(1, "b", "Rear");

    fun switchParamValue(swappedMapping: Boolean): Int = if (swappedMapping) {
        when (this) {
            FRONT -> REAR.switchValue
            REAR -> FRONT.switchValue
        }
    } else {
        switchValue
    }

    companion object {
        fun fromSuffix(value: String?): DashcamCamera? = entries.firstOrNull { it.suffix == value }
        fun fromPreference(value: String?): DashcamCamera = if (value == REAR.name) REAR else FRONT
    }
}
