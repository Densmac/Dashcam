package com.densmac.dashcam.core.network

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.DashcamDevice
import kotlinx.coroutines.flow.StateFlow

interface DashcamConnectionMonitor {
    val connectionState: StateFlow<DashcamConnectionState>
    fun startMonitoring()
    fun stopMonitoring()
    suspend fun probeOnce(): AppResult<DashcamDevice>
}
