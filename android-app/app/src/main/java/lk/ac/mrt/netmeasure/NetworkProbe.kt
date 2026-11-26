package lk.ac.mrt.netmeasure

import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Result of one probe: 3 latency pings + 1 download of the fixed 256 000-byte file. */
data class ProbeResult(
    val pingsMs: List<Double?>,          // 3 ping slots in order; null if that ping failed
    val dlBytes: Long?,                  // bytes received for /file
    val ttfbMs: Double?,                 // request start -> first body byte
    val dlTotalMs: Double?,              // request start -> last byte
    val error: String?,                  // first error, e.g. "ping2:SocketTimeoutException"
) {
    val ok: Boolean get() = error == null
    val latencyMedianMs: Double? get() = ProbeMath.median(pingsMs.filterNotNull())
    val latencyMeanMs: Double? get() = ProbeMath.mean(pingsMs.filterNotNull())
    val dlMbps: Double? get() = if (dlBytes != null && dlTotalMs != null) ProbeMath.mbps(dlBytes, dlTotalMs) else null
}

object ProbeMath {
    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    fun mean(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.sum() / xs.size

    /** Megabits per second from bytes and milliseconds (1 Mbit = 10^6 bit). */
    fun mbps(bytes: Long, ms: Double): Double? = if (ms <= 0) null else bytes * 8.0 / (ms * 1000.0)
}

/**
 * Probe against the laptop server (through ngrok) with HttpURLConnection.
 *
 * Latency: 3 x GET /ping. The first one may include DNS + TCP + TLS set-up; HttpURLConnection
 * keeps the connection alive, so later pings and the download usually reuse it. That is why we
 * report the median (robust to the set-up outlier) and also keep the mean and all raw values.
 *
 * Download speed = 256 000 B x 8 / (request start -> last byte). This includes one round trip,
 * which matters for a small file (TCP slow start) - noted as a limitation in the report.
 */
class NetworkProbe(private val baseUrl: String) {

    companion object {
        const val PINGS = 3
        const val FILE_SIZE = 256_000L        // must match FILE_SIZE in server/probe_server.py
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 8_000
    }

    fun run(): ProbeResult {
        val pings = ArrayList<Double?>(PINGS)
        var error: String? = null

        for (i in 1..PINGS) {
            try {
                val t0 = SystemClock.elapsedRealtimeNanos()
                val body = open("/ping").useBody { it.readBytes() }
                val t1 = SystemClock.elapsedRealtimeNanos()
                if (!body.contentEquals("pong".toByteArray())) throw IOException("bad ping body")
                pings += (t1 - t0) / 1e6
            } catch (e: Exception) {
                pings += null
                if (error == null) error = "ping$i:${e.javaClass.simpleName}"
            }
        }

        var bytes: Long? = null
        var ttfb: Double? = null
        var total: Double? = null
        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val conn = open("/file")
            var n = 0L
            conn.useBody { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    if (n == 0L && r > 0) ttfb = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                    n += r
                }
            }
            total = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            bytes = n
            if (n != FILE_SIZE && error == null) error = "download:size_$n"
        } catch (e: Exception) {
            if (error == null) error = "download:${e.javaClass.simpleName}"
        }

        return ProbeResult(pings, bytes, ttfb, total, error)
    }

    private fun open(path: String): HttpURLConnection {
        val url = URL("$baseUrl$path?n=${UUID.randomUUID()}") // nonce: defeat any cache
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("ngrok-skip-browser-warning", "1") // skip ngrok free-tier HTML page
            setRequestProperty("User-Agent", "NetMeasure/1.0")
        }
    }

    /**
     * Reads the body if the response is 200. We close the stream but do not call disconnect(),
     * so the socket goes back to the keep-alive pool for the next request.
     */
    private inline fun <T> HttpURLConnection.useBody(block: (java.io.InputStream) -> T): T {
        val code = responseCode
        if (code != 200) throw IOException("HTTP $code")
        return inputStream.use(block)
    }
}
