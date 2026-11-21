package lk.ac.mrt.netmeasure

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Logs connectivity changes of the phone's default network as EVENT rows
 * (deliverable 5: connection drops per walk and where they happened).
 *
 * Events: NET_AVAILABLE, NET_LOST, TRANSPORT_CHANGE (e.g. WIFI->CELL), VALIDATED / NOT_VALIDATED
 * (network up but internet unreachable, e.g. captive portal or dead AP).
 */
class NetworkMonitor(
    ctx: Context,
    private val onEvent: (event: String, detail: String) -> Unit,
) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    @Volatile private var transport: String = "NONE"
    @Volatile private var validated: Boolean? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val t = transportOf(cm.getNetworkCapabilities(network))
            onEvent("NET_AVAILABLE", t)
        }

        override fun onLost(network: Network) {
            onEvent("NET_LOST", transport)
            transport = "NONE"
            validated = null
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val t = transportOf(caps)
            if (t != transport) {
                if (transport != "NONE") onEvent("TRANSPORT_CHANGE", "$transport->$t")
                transport = t
            }
            val v = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated != null && v != validated) onEvent(if (v) "VALIDATED" else "NOT_VALIDATED", t)
            validated = v
        }
    }

    fun start() {
        transport = transportOf(cm.getNetworkCapabilities(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(callback)
    }

    fun stop() { runCatching { cm.unregisterNetworkCallback(callback) } }

    /** Transport of the current default network: WIFI, CELL, OTHER or NONE. */
    fun activeTransport(): String = transportOf(cm.getNetworkCapabilities(cm.activeNetwork))

    companion object {
        fun transportOf(caps: NetworkCapabilities?): String = when {
            caps == null -> "NONE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
            else -> "OTHER"
        }
    }
}
