package com.densmac.dashcam.domain.model

enum class RecordingState {
    UNKNOWN,
    ON,
    OFF
}

data class BatteryInfo(
    val capacity: Int,
    val charging: Boolean
)
