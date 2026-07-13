package com.densmac.dashcam.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val uuid: String,
    val model: String?,
    val softwareVersion: String,
    val hardwareVersion: String,
    val ssid: String,
    val cameraCount: Int,
    val soc: String?,
    val lastSeenAt: Long
)
