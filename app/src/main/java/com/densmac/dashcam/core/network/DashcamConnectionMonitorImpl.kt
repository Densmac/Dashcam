package com.densmac.dashcam.core.network

import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DispatchersProvider
import com.densmac.dashcam.core.common.userMessage
import com.densmac.dashcam.domain.model.DashcamConnectionState
import com.densmac.dashcam.domain.model.DashcamDevice
import com.densmac.dashcam.domain.repository.DashcamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashcamConnectionMonitorImpl @Inject constructor(
    private val dashcamRepository: DashcamRepository,
    private val dispatchers: DispatchersProvider
) : DashcamConnectionMonitor {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var monitorJob: Job? = null
    private var activeConsumers = 0
    private val _connectionState = MutableStateFlow<DashcamConnectionState>(DashcamConnectionState.Unknown)
    override val connectionState: StateFlow<DashcamConnectionState> = _connectionState

    @Synchronized
    override fun startMonitoring() {
        activeConsumers += 1
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (true) {
                probeOnce()
                delay(DashcamConstants.DEVICE_POLL_INTERVAL_MS)
            }
        }
    }

    @Synchronized
    override fun stopMonitoring() {
        activeConsumers = (activeConsumers - 1).coerceAtLeast(0)
        if (activeConsumers > 0) return
        monitorJob?.cancel()
        monitorJob = null
    }

    override suspend fun probeOnce(): AppResult<DashcamDevice> {
        _connectionState.value = DashcamConnectionState.Searching
        return when (val result = dashcamRepository.detectDevice()) {
            is AppResult.Success -> {
                _connectionState.value = DashcamConnectionState.Connected(result.data)
                result
            }
            is AppResult.Failure -> {
                _connectionState.value = DashcamConnectionState.ApiUnreachable(result.error.userMessage())
                result
            }
        }
    }
}
