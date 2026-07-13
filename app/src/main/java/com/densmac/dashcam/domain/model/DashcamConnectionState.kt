package com.densmac.dashcam.domain.model

sealed interface DashcamConnectionState {
    data object Unknown : DashcamConnectionState
    data object NotConnectedToWifi : DashcamConnectionState
    data object Searching : DashcamConnectionState
    data class Connected(val device: DashcamDevice) : DashcamConnectionState
    data class ApiUnreachable(val message: String) : DashcamConnectionState
    data class WrongWifi(val currentSsid: String?) : DashcamConnectionState
}
