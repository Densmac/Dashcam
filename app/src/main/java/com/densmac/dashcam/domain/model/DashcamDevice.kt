package com.densmac.dashcam.domain.model

data class DashcamDevice(
    val uuid: String,
    val model: String?,
    val softwareVersion: String,
    val hardwareVersion: String,
    val ssid: String,
    val cameraCount: Int,
    val currentCameraId: Int,
    val soc: String?
)
