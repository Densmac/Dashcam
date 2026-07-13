package com.densmac.dashcam.domain.model

data class DashcamSettings(
    val micEnabled: Boolean?,
    val osdEnabled: Boolean?,
    val recordingEnabled: Boolean?,
    val loopDuration: LoopDuration?,
    val speakerLevel: LevelSetting?,
    val gSensorSensitivity: LevelSetting?,
    val timelapseRate: TimelapseRate?,
    val resolutionLabel: String?
)

enum class LoopDuration(val value: Int, val label: String) {
    ONE_MIN(0, "1 min"),
    TWO_MIN(1, "2 min"),
    THREE_MIN(2, "3 min");

    companion object {
        fun fromValue(value: Int?) = entries.firstOrNull { it.value == value }
    }
}

enum class LevelSetting(val value: Int, val label: String) {
    OFF(0, "Off"),
    LOW(1, "Low"),
    MIDDLE(2, "Middle"),
    HIGH(3, "High");

    companion object {
        fun fromValue(value: Int?) = entries.firstOrNull { it.value == value }
    }
}

enum class TimelapseRate(val value: Int, val label: String) {
    OFF(0, "Off"),
    ONE_SECOND(1, "1 sec"),
    TWO_SECONDS(2, "2 sec"),
    THREE_SECONDS(3, "3 sec");

    companion object {
        fun fromValue(value: Int?) = entries.firstOrNull { it.value == value }
    }
}
