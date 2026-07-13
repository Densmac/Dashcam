package com.densmac.dashcam.core.player

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamCamera
import kotlinx.coroutines.flow.StateFlow

interface LivePreviewEngine {
    val state: StateFlow<LivePreviewState>
    fun attach(surfaceHost: Any)
    suspend fun start(url: String): AppResult<Unit>
    suspend fun switchCamera(camera: DashcamCamera): AppResult<Unit>
    suspend fun stop()
    fun release()
}
