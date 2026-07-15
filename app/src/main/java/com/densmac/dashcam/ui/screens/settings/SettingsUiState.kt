package com.densmac.dashcam.ui.screens.settings

import com.densmac.dashcam.data.datastore.UserPreferences
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.DashcamSettings
import com.densmac.dashcam.domain.model.StorageStatus

data class SettingsUiState(
    val connectionState: DashcamConnectionState = DashcamConnectionState.Unknown,
    val dashcamSettings: DashcamSettings? = null,
    val storageStatus: StorageStatus? = null,
    val preferences: UserPreferences = UserPreferences(),
    val loading: Boolean = false,
    val message: String? = null,
    val confirmStopRecording: Boolean = false,
    val editWifiSsid: Boolean = false,
    val editWifiPassword: Boolean = false,
    val confirmFormat: Boolean = false
) {
    /** Current dashcam Wi-Fi SSID, when connected. */
    val currentSsid: String?
        get() = (connectionState as? DashcamConnectionState.Connected)?.device?.ssid
}
