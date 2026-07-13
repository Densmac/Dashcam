package com.densmac.dashcam.core.network

import android.net.Network
import com.densmac.dashcam.core.common.AppResult
import kotlinx.coroutines.flow.StateFlow

interface DashcamNetworkBinder {
    val boundNetwork: StateFlow<Network?>
    suspend fun findAndBindDashcamNetwork(): AppResult<Network>
    fun unbind()
}
