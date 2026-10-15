package com.survival.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect

enum class ConnectionType(val label: String) {
    WIFI("Wi-Fi"),
    MOBILE("Mobile"),
    ETHERNET("Ethernet"),
    VPN("VPN"),
    OTHER("Autre"),
    NONE("Aucune")
}

data class ConnectionInfo(
    val type: ConnectionType,
    val validated: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val metered: Boolean
) {
    val isConnected: Boolean get() = type != ConnectionType.NONE

    companion object {
        val Offline = ConnectionInfo(ConnectionType.NONE, false, 0, 0, false)
    }
}

private fun NetworkCapabilities.toConnectionInfo(): ConnectionInfo {
    // VPN first: a VPN network also reports the transport of the network it runs over.
    val type = when {
        hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
        else -> ConnectionType.OTHER
    }
    val notMetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED))
    return ConnectionInfo(
        type = type,
        validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        downstreamKbps = linkDownstreamBandwidthKbps,
        upstreamKbps = linkUpstreamBandwidthKbps,
        metered = !notMetered
    )
}

private fun ConnectivityManager.currentConnection(): ConnectionInfo {
    val network = activeNetwork ?: return ConnectionInfo.Offline
    return getNetworkCapabilities(network)?.toConnectionInfo() ?: ConnectionInfo.Offline
}

/** Follows the default network; callbacks are delivered on the main looper. */
private class DefaultNetworkWatcher(
    private val manager: ConnectivityManager,
    private val onChange: (ConnectionInfo) -> Unit
) : ConnectivityManager.NetworkCallback() {

    fun start() {
        onChange(manager.currentConnection())
        manager.registerDefaultNetworkCallback(this, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        manager.unregisterNetworkCallback(this)
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        onChange(networkCapabilities.toConnectionInfo())
    }

    override fun onLost(network: Network) {
        onChange(
            if (manager.activeNetwork == network) ConnectionInfo.Offline else manager.currentConnection()
        )
    }
}

@Composable
fun rememberConnectionInfo(): ConnectionInfo {
    val context = LocalContext.current
    val manager: ConnectivityManager? = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var info by remember { mutableStateOf(manager?.currentConnection() ?: ConnectionInfo.Offline) }
    LifecycleStartEffect(manager) {
        val watcher = manager?.let { cm -> DefaultNetworkWatcher(cm) { info = it }.also { it.start() } }
        onStopOrDispose { watcher?.stop() }
    }
    return info
}
