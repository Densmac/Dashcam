package com.densmac.dashcam.domain.model

enum class DashcamFolder(val apiValue: String, val displayName: String) {
    LOOP("loop", "Recordings"),
    EVENT("event", "Snapshots"),
    PARK("park", "Parking"),
    EMR("emr", "Emergency"),
    RACE("race", "Race");

    companion object {
        fun fromApiValue(value: String?): DashcamFolder = entries.firstOrNull { it.apiValue == value } ?: LOOP
    }
}
