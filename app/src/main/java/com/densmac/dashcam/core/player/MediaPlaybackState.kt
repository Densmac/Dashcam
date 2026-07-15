package com.densmac.dashcam.core.player

import com.densmac.dashcam.core.common.AppError

/** Sealed UI states for the in-app media viewer (remote streaming or local playback). */
sealed interface MediaPlaybackState {
    data object Idle : MediaPlaybackState
    data object Opening : MediaPlaybackState
    data object Buffering : MediaPlaybackState
    data object Playing : MediaPlaybackState
    data object Paused : MediaPlaybackState
    data object Ended : MediaPlaybackState
    data class Error(val error: AppError) : MediaPlaybackState
}
