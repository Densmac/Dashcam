package com.densmac.dashcam.core.player

import com.densmac.dashcam.core.common.AppError

sealed interface LivePreviewState {
    data object Idle : LivePreviewState
    data object Preparing : LivePreviewState
    data object Connecting : LivePreviewState
    data object Playing : LivePreviewState
    data object Buffering : LivePreviewState
    data class Error(val error: AppError) : LivePreviewState
    data object Released : LivePreviewState
}
