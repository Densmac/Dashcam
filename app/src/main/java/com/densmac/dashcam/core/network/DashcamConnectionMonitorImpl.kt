package com.densmac.dashcam.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    private val connectivityManager: ConnectivityManager,
    private val dashcamRepository: DashcamRepository,
    private val dispatchers: DispatchersProvider
) : DashcamConnectionMonitor {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var monitorJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeConsumers = 0
    private var consecutiveFailures = 0
    private val _connectionState = MutableStateFlow<DashcamConnectionState>(DashcamConnectionState.Unknown)
    override val connectionState: StateFlow<DashcamConnectionState> = _connectionState

    @Synchronized
    override fun startMonitoring() {
        activeConsumers += 1
        registerNetworkCallbackIfNeeded()
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
        unregisterNetworkCallback()
    }

    override suspend fun probeOnce(): AppResult<DashcamDevice> {
        if (_connectionState.value == DashcamConnectionState.Unknown) {
            _connectionState.value = DashcamConnectionState.Searching
        }
        return when (val result = dashcamRepository.detectDevice()) {
            is AppResult.Success -> {
                consecutiveFailures = 0
                _connectionState.value = DashcamConnectionState.Connected(result.data)
                result
            }
            is AppResult.Failure -> {
                consecutiveFailures += 1
                val currentlyConnected = _connectionState.value is DashcamConnectionState.Connected
                if (!currentlyConnected || consecutiveFailures >= 2) {
                    _connectionState.value = DashcamConnectionState.ApiUnreachable(result.error.userMessage())
                }
                result
            }
        }
    }

    @Synchronized
    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                probeSoon()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                probeSoon()
            }

            override fun onLost(network: Network) {
                probeSoon()
            }
        }
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
            networkCallback = callback
        }
    }

    @Synchronized
    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun probeSoon() {
        if (activeConsumers <= 0) return
        scope.launch { probeOnce() }
    }
}
