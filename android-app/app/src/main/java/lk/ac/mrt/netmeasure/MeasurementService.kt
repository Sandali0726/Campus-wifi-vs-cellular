package lk.ac.mrt.netmeasure

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service (type = location) that drives the measurement:
 *  - a probe every [PROBE_INTERVAL_MS] (fixed-rate timer),
 *  - an immediate probe when a waypoint is tapped,
 *  - if a probe is still running when the next one is due, the new one is SKIPPED and logged.
 * Every probe, skip, waypoint tap and network event becomes one CSV row.
 */
class MeasurementService : Service() {

    companion object {
        const val ACTION_START = "lk.ac.mrt.netmeasure.START"
        const val ACTION_STOP = "lk.ac.mrt.netmeasure.STOP"
        const val ACTION_WAYPOINT = "lk.ac.mrt.netmeasure.WAYPOINT"
        const val EXTRA_WAYPOINT = "waypoint"

        const val PROBE_INTERVAL_MS = 10_000L
        private const val CHANNEL_ID = "measurement"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "MeasurementService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private val probeBusy = AtomicBoolean(false)

    private var config: RunConfig? = null
    private var csv: CsvLogger? = null
    private var probe: NetworkProbe? = null
    private var wifi: WifiSignalReader? = null
    private var cell: CellSignalReader? = null
    private var location: LocationTracker? = null
    private var netMonitor: NetworkMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var currentWaypoint: String = ""
    private var runStartElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> RunConfig.from(intent)?.let { startRun(it) }
            ACTION_WAYPOINT -> intent.getStringExtra(EXTRA_WAYPOINT)?.let { onWaypoint(it) }
            ACTION_STOP -> stopRun()
        }
        return START_NOT_STICKY // a crashed run must not silently restart with stale settings
    }

    // ---------------------------------------------------------------- run lifecycle

    private fun startRun(cfg: RunConfig) {
        if (timerJob != null) return // already running
        // Must be called promptly after startForegroundService(); location permission is
        // already granted by MainActivity (required for type=location on Android 14+).
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification("Starting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )

        config = cfg
        currentWaypoint = ""
        runStartElapsed = SystemClock.elapsedRealtime()

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetMeasure:run")
            .apply { acquire(3 * 60 * 60 * 1000L) } // safety cap: 3 h

        val logger = CsvLogger.create(this, cfg)
        csv = logger
        probe = NetworkProbe(cfg.serverUrl)
        wifi = WifiSignalReader(this, onApChange = { from, to -> logEvent("WIFI_AP_CHANGE", "$from->$to") })
            .also { it.start() }
        cell = CellSignalReader(this, onTypeChange = { from, to -> logEvent("CELL_TYPE_CHANGE", "$from->$to") })
            .also { it.start() }
        location = LocationTracker(this).also { it.start() }
        netMonitor = NetworkMonitor(this) { event, detail -> logEvent(event, detail) }.also { it.start() }

        MeasurementState.reset(MeasurementState.Status(running = true, fileName = logger.file.name))
        logEvent("RUN_START", "role=${cfg.netRole} slot=${cfg.timeSlot} walk=${cfg.walkId} url=${cfg.serverUrl}")

        // Fixed-rate timer: next tick is scheduled from the previous *scheduled* time,
        // so a slow probe does not shift the whole schedule.
        timerJob = scope.launch {
            var next = SystemClock.elapsedRealtime()
            while (isActive) {
                triggerProbe("TIMER")
                next += PROBE_INTERVAL_MS
                delay((next - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            }
        }
    }

    private fun stopRun() {
        if (timerJob == null) { stopSelf(); return }
        logEvent("RUN_STOP", "")
        timerJob?.cancel(); timerJob = null
        netMonitor?.stop(); wifi?.stop(); cell?.stop(); location?.stop()
        csv?.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        MeasurementState.update { it.copy(running = false, waypoint = null) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (timerJob != null) stopRun()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- waypoints & probes

    private fun onWaypoint(label: String) {
        if (timerJob == null) return
        currentWaypoint = label
        MeasurementState.update { it.copy(waypoint = label, waypointTapEpochMs = System.currentTimeMillis()) }
        write(baseRow("WAYPOINT", "TAP"))
        triggerProbe("TAP")
    }

    /** Starts a probe unless one is still running; in that case logs a SKIPPED row. */
    private fun triggerProbe(trigger: String) {
        if (!probeBusy.compareAndSet(false, true)) {
            write(baseRow("PROBE", trigger).copy(status = "SKIPPED", error = "previous probe still running"))
            MeasurementState.update { it.copy(skipped = it.skipped + 1) }
            return
        }
        scope.launch {
            try {
                val before = baseRow("PROBE", trigger) // signal + location at probe start
                val r = probe!!.run()
                val row = before.withProbe(r)
                write(row)
                MeasurementState.update {
                    if (r.ok) it.copy(ok = it.ok + 1) else it.copy(failed = it.failed + 1)
                }
                updateNotification(row)
            } catch (t: Throwable) {
                Log.e(TAG, "probe crashed", t)
                write(baseRow("PROBE", trigger).copy(status = "FAILED", error = "crash:${t.javaClass.simpleName}"))
                MeasurementState.update { it.copy(failed = it.failed + 1) }
            } finally {
                probeBusy.set(false)
            }
        }
    }

    private fun logEvent(event: String, detail: String) {
        if (csv == null) return
        write(baseRow("EVENT", "").copy(event = event, error = detail))
        MeasurementState.update { it.copy(events = it.events + 1) }
    }

    // ---------------------------------------------------------------- rows

    /** A row with run info, current waypoint, signal and location filled in. */
    private fun baseRow(rowType: String, trigger: String): MeasurementRow {
        val cfg = config!!
        val w = wifi?.snapshot()
        val c = cell?.snapshot()
        val l = location?.snapshot()
        val transport = netMonitor?.activeTransport() ?: "UNKNOWN"
        val warn = when {
            cfg.netRole == "WIFI" && transport == "CELL" -> "Role is Wi-Fi but phone is on cellular: turn mobile data OFF"
            cfg.netRole == "CELL" && transport == "WIFI" -> "Role is cellular but phone is on Wi-Fi: turn Wi-Fi OFF"
            else -> null
        }
        MeasurementState.update { it.copy(warning = warn) }
        return MeasurementRow(
            epochMs = System.currentTimeMillis(),
            elapsedMs = SystemClock.elapsedRealtime() - runStartElapsed,
            phoneId = cfg.phoneId, netRole = cfg.netRole, timeSlot = cfg.timeSlot, walkId = cfg.walkId,
            rowType = rowType, trigger = trigger, waypoint = currentWaypoint,
            activeTransport = transport,
            wifiRssi = w?.rssi, wifiLinkMbps = w?.linkMbps, wifiFreqMhz = w?.freqMhz, wifiBssidHash = w?.bssidHash,
            cellDbm = c?.dbm, cellLevel = c?.level, cellRsrp = c?.rsrp, cellRsrq = c?.rsrq, cellSinr = c?.sinr,
            netType = c?.networkType, operator = c?.operator,
            lat = l?.lat, lon = l?.lon, locAccuracyM = l?.accuracyM, locAgeS = l?.ageS,
        )
    }

    private fun write(row: MeasurementRow) {
        csv?.append(row)
        MeasurementState.update { it.copy(rows = it.rows + 1, lastRowSummary = row.summary()) }
    }

    // ---------------------------------------------------------------- notification

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Measurement", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("NetMeasure running")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(row: MeasurementRow) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(row.summary().lineSequence().first()))
    }
}
