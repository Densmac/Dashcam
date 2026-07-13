package com.densmac.dashcam.ui.screens.live

import com.densmac.dashcam.core.player.LivePreviewState
import com.densmac.dashcam.domain.model.DashcamCamera
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.DashcamSettings
import com.densmac.dashcam.domain.model.StorageStatus

data class LiveUiState(
    val connectionState: DashcamConnectionState = DashcamConnectionState.Unknown,
    val previewState: LivePreviewState = LivePreviewState.Idle,
    val selectedCamera: DashcamCamera = DashcamCamera.FRONT,
    val settings: DashcamSettings? = null,
    val storageStatus: StorageStatus? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val confirmStopRecording: Boolean = false,
    val snapshotSuccess: Boolean = false
)
