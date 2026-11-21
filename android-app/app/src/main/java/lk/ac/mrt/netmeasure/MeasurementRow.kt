package lk.ac.mrt.netmeasure

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One CSV row. Row types:
 *  PROBE    - a probe (status OK / FAILED / SKIPPED), trigger TIMER or TAP
 *  WAYPOINT - the moment a waypoint button was tapped (ground-truth position)
 *  EVENT    - a network event (NET_LOST, NET_AVAILABLE, TRANSPORT_CHANGE, WIFI_AP_CHANGE, ...)
 * Empty cells mean "not available" (e.g. no Wi-Fi RSSI on the cellular phone).
 */
data class MeasurementRow(
    val epochMs: Long,
    val elapsedMs: Long,
    val phoneId: String,
    val netRole: String,
    val timeSlot: String,
    val walkId: Int,
    val rowType: String,
    val trigger: String,
    val waypoint: String,
    val status: String = "",
    val error: String = "",
    val event: String = "",
    val activeTransport: String = "",
    val netType: String? = null,
    val operator: String? = null,
    val wifiRssi: Int? = null,
    val wifiLinkMbps: Int? = null,
    val wifiFreqMhz: Int? = null,
    val wifiBssidHash: String? = null,
    val cellDbm: Int? = null,
    val cellLevel: Int? = null,
    val cellRsrp: Int? = null,
    val cellRsrq: Int? = null,
    val cellSinr: Int? = null,
    val cellNrRsrp: Int? = null,
    val ping1Ms: Double? = null,
    val ping2Ms: Double? = null,
    val ping3Ms: Double? = null,
    val latencyMedianMs: Double? = null,
    val latencyMeanMs: Double? = null,
    val ttfbMs: Double? = null,
    val dlBytes: Long? = null,
    val dlTotalMs: Double? = null,
    val dlMbps: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val locAccuracyM: Float? = null,
    val locAgeS: Long? = null,
) {
    fun withProbe(r: ProbeResult) = copy(
        status = if (r.ok) "OK" else "FAILED",
        error = r.error ?: "",
        ping1Ms = r.pingsMs.getOrNull(0), ping2Ms = r.pingsMs.getOrNull(1), ping3Ms = r.pingsMs.getOrNull(2),
        latencyMedianMs = r.latencyMedianMs, latencyMeanMs = r.latencyMeanMs,
        ttfbMs = r.ttfbMs, dlBytes = r.dlBytes, dlTotalMs = r.dlTotalMs, dlMbps = r.dlMbps,
    )

    fun toCsvLine(): String = listOf(
        ISO.format(Instant.ofEpochMilli(epochMs)), epochMs, elapsedMs,
        phoneId, netRole, timeSlot, walkId, rowType, trigger, waypoint, status, error, event,
        activeTransport, netType, operator,
        wifiRssi, wifiLinkMbps, wifiFreqMhz, wifiBssidHash,
        cellDbm, cellLevel, cellRsrp, cellRsrq, cellSinr, cellNrRsrp,
        ping1Ms.f(2), ping2Ms.f(2), ping3Ms.f(2), latencyMedianMs.f(2), latencyMeanMs.f(2),
        ttfbMs.f(2), dlBytes, dlTotalMs.f(2), dlMbps.f(3),
        lat, lon, locAccuracyM?.toDouble().f(1), locAgeS,
    ).joinToString(",") { escape(it?.toString() ?: "") }

    /** Short human-readable text for the screen and the notification. */
    fun summary(): String = buildString {
        append("$rowType ${trigger.ifEmpty { event }} ${waypoint.ifEmpty { "-" }} ${status}")
        appendLine()
        append("net=$activeTransport ${netType ?: ""}  ")
        wifiRssi?.let { append("wifi=${it}dBm  ") }
        cellDbm?.let { append("cell=${it}dBm(L$cellLevel)  ") }
        appendLine()
        latencyMedianMs?.let { append("lat=${"%.0f".format(Locale.US, it)}ms  ") }
        dlMbps?.let { append("dl=${"%.2f".format(Locale.US, it)}Mbps  ") }
        if (error.isNotEmpty()) append("err=$error")
    }

    companion object {
        val HEADER = listOf(
            "timestamp_iso", "epoch_ms", "elapsed_ms",
            "phone_id", "net_role", "time_slot", "walk_id", "row_type", "trigger", "waypoint",
            "status", "error", "event",
            "active_transport", "net_type", "operator",
            "wifi_rssi", "wifi_link_mbps", "wifi_freq_mhz", "wifi_bssid_hash",
            "cell_dbm", "cell_level", "cell_rsrp", "cell_rsrq", "cell_sinr", "cell_nr_rsrp",
            "ping1_ms", "ping2_ms", "ping3_ms", "latency_median_ms", "latency_mean_ms",
            "ttfb_ms", "dl_bytes", "dl_total_ms", "dl_mbps",
            "lat", "lon", "loc_accuracy_m", "loc_age_s",
        ).joinToString(",")

        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault())

        private fun Double?.f(decimals: Int): String? = this?.let { "%.${decimals}f".format(Locale.US, it) }

        fun escape(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
