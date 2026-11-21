package lk.ac.mrt.netmeasure

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import java.util.concurrent.Executors

/**
 * Cellular signal strength and network type from TelephonyManager.
 *
 * - Signal: SignalStrength.getCellSignalStrengths() → dBm, level 0–4, LTE RSRP/RSRQ/RSSNR, NR SS-RSRP.
 * - Type: getDataNetworkType() (needs READ_PHONE_STATE), refined with TelephonyDisplayInfo so that
 *   5G non-standalone (shown as "5G" in the status bar but reported as LTE) is detected.
 */
class CellSignalReader(
    private val ctx: Context,
    private val onTypeChange: (from: String, to: String) -> Unit,
) {
    data class Snapshot(
        val dbm: Int?, val level: Int?, val rsrp: Int?, val rsrq: Int?, val sinr: Int?,
        val nrRsrp: Int?, val networkType: String, val operator: String?,
    )

    private val tm = ctx.getSystemService(TelephonyManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var displayOverride: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var lastType: String? = null
    private var callback: TelephonyCallback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                displayOverride = info.overrideNetworkType
                checkTypeChange()
            }
        }
        runCatching { tm.registerTelephonyCallback(executor, cb) }
        callback = cb
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback?.let { runCatching { tm.unregisterTelephonyCallback(it) } }
        }
        callback = null
        executor.shutdown()
    }

    fun snapshot(): Snapshot {
        val ss = runCatching { tm.signalStrength }.getOrNull()
        val strengths = ss?.cellSignalStrengths.orEmpty()
        val lte = strengths.filterIsInstance<CellSignalStrengthLte>().firstOrNull()
        val nr = strengths.filterIsInstance<CellSignalStrengthNr>().firstOrNull()
        val primary = lte ?: nr ?: strengths.firstOrNull()
        val type = currentType()
        checkTypeChange(type)
        return Snapshot(
            dbm = primary?.dbm?.valid(),
            level = ss?.level,
            rsrp = lte?.rsrp?.valid() ?: nr?.ssRsrp?.valid(),
            rsrq = lte?.rsrq?.valid() ?: nr?.ssRsrq?.valid(),
            sinr = lte?.rssnr?.valid() ?: nr?.ssSinr?.valid(),
            nrRsrp = nr?.ssRsrp?.valid(),
            networkType = type,
            operator = tm.networkOperatorName?.takeIf { it.isNotBlank() },
        )
    }

    @SuppressLint("MissingPermission")
    private fun currentType(): String {
        val raw = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return when (displayOverride) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "NR_NSA"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "LTE_A"
            else -> typeName(raw)
        }
    }

    private fun checkTypeChange(type: String = currentType()) {
        val prev = lastType
        lastType = type // update first: logging the event reads the snapshot again
        if (prev != null && prev != type) onTypeChange(prev, type)
    }

    companion object {
        private fun Int.valid(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MIN_VALUE }

        fun typeName(t: Int): String = when (t) {
            TelephonyManager.NETWORK_TYPE_NR -> "NR_SA"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "NONE"
            else -> "OTHER_$t"
        }
    }
}
