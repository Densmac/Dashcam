package com.densmac.dashcam.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.densmac.dashcam.core.common.AppError
import com.densmac.dashcam.core.common.AppResult
import com.densmac.dashcam.core.common.DashcamConstants
import com.densmac.dashcam.core.common.DispatchersProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashcamNetworkBinderImpl @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val dispatchers: DispatchersProvider
) : DashcamNetworkBinder {
    private val _boundNetwork = MutableStateFlow<Network?>(null)
    override val boundNetwork: StateFlow<Network?> = _boundNetwork

    override suspend fun findAndBindDashcamNetwork(): AppResult<Network> = withContext(dispatchers.io) {
        val networks = connectivityManager.allNetworks.filter { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        // A network whose link address/gateway is on the dashcam subnet is a strong,
        // load-independent signal. Bind on it directly instead of an HTTP verify: the
        // verify request competes with in-flight thumbnail/download traffic and can time
        // out under load, which would falsely report NotConnectedToDashcam and break
        // resuming the live feed after visiting the Library.
        networks.firstOrNull { looksLikeDashcamNetwork(it) }?.let { network ->
            connectivityManager.bindProcessToNetwork(network)
            _boundNetwork.value = network
            return@withContext AppResult.Success(network)
        }
        // Fallback: link properties did not identify the dashcam subnet, so confirm the
        // network answers the dashcam HTTP endpoint before binding.
        for (network in networks) {
            if (verify(network)) {
                connectivityManager.bindProcessToNetwork(network)
                _boundNetwork.value = network
                return@withContext AppResult.Success(network)
            }
        }
        connectivityManager.bindProcessToNetwork(null)
        _boundNetwork.value = null
        AppResult.Failure(AppError.NotConnectedToDashcam)
    }

    override fun unbind() {
        connectivityManager.bindProcessToNetwork(null)
        _boundNetwork.value = null
    }

    private fun looksLikeDashcamNetwork(network: Network): Boolean {
        val links = connectivityManager.getLinkProperties(network) ?: return false
        val addressMatch = links.linkAddresses.any {
            it.address.hostAddress?.startsWith("192.168.169.") == true
        }
        val routeMatch = links.routes.any {
            it.gateway?.hostAddress == DashcamConstants.DEFAULT_HOST
        }
        return addressMatch || routeMatch
    }

    private fun verify(network: Network): Boolean = runCatching {
        val url = URL("${DashcamConstants.HTTP_BASE_URL}${DashcamConstants.ENDPOINT_GET_DEVICE_ATTR}")
        val connection = network.openConnection(url) as HttpURLConnection
        try {
            connection.connectTimeout = 2_500
            connection.readTimeout = 2_500
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { it.readText().contains("\"result\":0") }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)
}
