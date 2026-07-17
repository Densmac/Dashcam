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

/**
 * Current playback progress. [position] (0f..1f) comes straight from LibVLC and is always valid
 * for a seekable Range stream even when the container duration is unknown (common for a raw .ts
 * streamed over HTTP), so the scrubber is driven by it rather than by positionMs/durationMs.
 */
data class MediaProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val position: Float = 0f,
    val seekable: Boolean = false
)
