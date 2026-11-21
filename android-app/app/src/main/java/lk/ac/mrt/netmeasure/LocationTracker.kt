package lk.ac.mrt.netmeasure

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import kotlin.math.roundToLong

/**
 * Coarse position for each row, from the platform LocationManager (GPS + network providers).
 * Position on the route comes from waypoint taps (ground truth); GPS is only supporting context,
 * so coordinates are rounded to 4 decimals (about 11 m).
 */
class LocationTracker(ctx: Context) {

    data class Snapshot(val lat: Double, val lon: Double, val accuracyM: Float, val ageS: Long)

    private val lm = ctx.getSystemService(LocationManager::class.java)
    @Volatile private var best: Location? = null

    private val listener = LocationListener { loc -> if (isBetter(loc, best)) best = loc }

    @SuppressLint("MissingPermission") // granted in MainActivity before the service starts
    fun start() {
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) continue
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { if (isBetter(it, best)) best = it }
            runCatching { lm.requestLocationUpdates(p, 5_000L, 0f, listener, Looper.getMainLooper()) }
        }
    }

    fun stop() { runCatching { lm.removeUpdates(listener) } }

    fun snapshot(): Snapshot? {
        val l = best ?: return null
        val ageS = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000_000
        return Snapshot(round4(l.latitude), round4(l.longitude), l.accuracy, ageS)
    }

    companion object {
        fun round4(x: Double): Double = (x * 10_000).roundToLong() / 10_000.0

        /** Newer fixes win, unless an older one (< 10 s older) is much more accurate. */
        fun isBetter(new: Location, old: Location?): Boolean {
            if (old == null) return true
            val dtS = (new.elapsedRealtimeNanos - old.elapsedRealtimeNanos) / 1e9
            if (dtS > 10) return true
            if (dtS < -10) return false
            return new.accuracy <= old.accuracy * 1.5f || dtS > 0 && new.accuracy < 50
        }
    }
}
