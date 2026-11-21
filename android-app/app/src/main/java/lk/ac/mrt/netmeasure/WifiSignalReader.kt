package lk.ac.mrt.netmeasure

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.security.MessageDigest

/**
 * Wi-Fi RSSI, link speed, frequency and (hashed) access-point BSSID.
 *
 * Android 12+ (API 31): WifiManager.getConnectionInfo() is deprecated and redacts location-sensitive
 * fields, so the WifiInfo is taken from NetworkCapabilities.transportInfo of a callback registered with
 * FLAG_INCLUDE_LOCATION_INFO (needs ACCESS_FINE_LOCATION). Older versions fall back to WifiManager.
 */
class WifiSignalReader(
    private val ctx: Context,
    private val onApChange: (from: String, to: String) -> Unit,
) {
    data class Snapshot(val rssi: Int?, val linkMbps: Int?, val freqMhz: Int?, val bssidHash: String?)

    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)

    @Volatile private var latest: WifiInfo? = null
    @Volatile private var lastBssidHash: String? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cb = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Fires on connect and whenever RSSI / BSSID changes.
                (caps.transportInfo as? WifiInfo)?.let { info ->
                    latest = info
                    checkApChange(hashBssid(info.bssid))
                }
            }
            override fun onLost(network: Network) { latest = null }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(request, cb)
        callback = cb
    }

    fun stop() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun snapshot(): Snapshot? {
        if (!wm.isWifiEnabled) return null
        val info = latest ?: runCatching { wm.connectionInfo }.getOrNull() ?: return null
        if (info.rssi <= INVALID_RSSI) return null // not connected
        val hash = hashBssid(info.bssid)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) checkApChange(hash)
        return Snapshot(
            rssi = info.rssi,
            linkMbps = info.linkSpeed.takeIf { it > 0 },
            freqMhz = info.frequency.takeIf { it > 0 },
            bssidHash = hash,
        )
    }

    private fun checkApChange(hash: String?) {
        if (hash == null) return
        val prev = lastBssidHash
        lastBssidHash = hash // update first: logging the event reads the snapshot again
        if (prev != null && hash != prev) onApChange(prev, hash)
    }

    companion object {
        private const val INVALID_RSSI = -127

        /**
         * Short SHA-256 of the AP's BSSID: lets us see access-point hand-overs without storing
         * the raw hardware address (assignment privacy rule).
         */
        fun hashBssid(bssid: String?): String? {
            if (bssid == null || bssid == "02:00:00:00:00:00") return null // redacted / unknown
            val d = MessageDigest.getInstance("SHA-256").digest(bssid.lowercase().toByteArray())
            return d.take(4).joinToString("") { "%02x".format(it) }
        }
    }
}
