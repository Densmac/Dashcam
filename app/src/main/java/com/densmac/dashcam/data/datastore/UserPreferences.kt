package com.densmac.dashcam.data.datastore

import com.densmac.dashcam.domain.model.DashcamCamera

data class UserPreferences(
    val preferredCamera: DashcamCamera = DashcamCamera.FRONT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val autoStartLivePreview: Boolean = false,
    val showDebugDiagnostics: Boolean = false,
    val cameraMappingSwapped: Boolean = false,
    val lastKnownDeviceUuid: String? = null
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
