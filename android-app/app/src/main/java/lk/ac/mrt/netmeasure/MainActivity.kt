package lk.ac.mrt.netmeasure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main screen: choose phone / role / time slot / walk, Start/Stop the measurement service,
 * tap waypoints P1–P5, and watch the last logged row live.
 */
class MainActivity : ComponentActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var waypointButtons: List<Button>
    private lateinit var configControls: List<android.view.View>
    private lateinit var tvWaypoint: TextView
    private lateinit var tvStatus: TextView

    /** Permissions that must be granted before the location-type foreground service may start. */
    private val requiredPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val missing = result.filterValues { !it }.keys
                .filter { it != Manifest.permission.POST_NOTIFICATIONS } // notification is optional
            if (missing.isEmpty()) startMeasurement()
            else toast("Cannot start: permission denied (${missing.joinToString { it.substringAfterLast('.') }})")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvWaypoint = findViewById(R.id.tvWaypoint)
        tvStatus = findViewById(R.id.tvStatus)
        waypointButtons = listOf(R.id.btnP1, R.id.btnP2, R.id.btnP3, R.id.btnP4, R.id.btnP5)
            .map { findViewById(it) }
        configControls = listOf(
            R.id.rbPhoneA, R.id.rbPhoneB,
            R.id.rbWifi, R.id.rbCell,
            R.id.rbOffpeak, R.id.rbPeak,
            R.id.etWalk, R.id.etServer,
        ).map { findViewById(it) }

        restoreConfig(RunConfig.load(this))

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener {
            startService(Intent(this, MeasurementService::class.java).setAction(MeasurementService.ACTION_STOP))
        }
        waypointButtons.forEachIndexed { i, b ->
            b.setOnClickListener {
                startService(
                    Intent(this, MeasurementService::class.java)
                        .setAction(MeasurementService.ACTION_WAYPOINT)
                        .putExtra(MeasurementService.EXTRA_WAYPOINT, "P${i + 1}")
                )
            }
        }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareLastCsv() }

        // Live status: re-render on every state change and once a second (for the waypoint timer).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { MeasurementState.status.collect { render(it) } }
                launch { while (true) { delay(1000); render(MeasurementState.status.value) } }
            }
        }
    }

    private fun onStartClicked() {
        val url = findViewById<EditText>(R.id.etServer).text.toString().trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Enter the server URL (https://…)")
            return
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startMeasurement() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startMeasurement() {
        val cfg = readConfig()
        cfg.save(this)
        val intent = cfg.putInto(Intent(this, MeasurementService::class.java).setAction(MeasurementService.ACTION_START))
        ContextCompat.startForegroundService(this, intent)
    }

    private fun readConfig() = RunConfig(
        phoneId = if (findViewById<RadioButton>(R.id.rbPhoneB).isChecked) "B" else "A",
        netRole = if (findViewById<RadioButton>(R.id.rbCell).isChecked) "CELL" else "WIFI",
        timeSlot = if (findViewById<RadioButton>(R.id.rbPeak).isChecked) "PEAK" else "OFFPEAK",
        walkId = findViewById<EditText>(R.id.etWalk).text.toString().toIntOrNull() ?: 1,
        serverUrl = findViewById<EditText>(R.id.etServer).text.toString().trim().trimEnd('/'),
    )

    private fun restoreConfig(c: RunConfig) {
        findViewById<RadioButton>(if (c.phoneId == "B") R.id.rbPhoneB else R.id.rbPhoneA).isChecked = true
        findViewById<RadioButton>(if (c.netRole == "CELL") R.id.rbCell else R.id.rbWifi).isChecked = true
        findViewById<RadioButton>(if (c.timeSlot == "PEAK") R.id.rbPeak else R.id.rbOffpeak).isChecked = true
        findViewById<EditText>(R.id.etWalk).setText(c.walkId.toString())
        findViewById<EditText>(R.id.etServer).setText(c.serverUrl)
    }

    private fun render(s: MeasurementState.Status) {
        btnStart.isEnabled = !s.running
        btnStop.isEnabled = s.running
        waypointButtons.forEach { it.isEnabled = s.running }
        configControls.forEach { it.isEnabled = !s.running }

        tvWaypoint.text = if (s.waypoint == null) "Waypoint: –" else {
            val secs = (System.currentTimeMillis() - s.waypointTapEpochMs) / 1000
            "Waypoint: ${s.waypoint}  (${secs}s)" + if (secs >= 40) "  ✔ walk on" else ""
        }

        tvStatus.text = buildString {
            appendLine(if (s.running) "RUNNING" else "Not running")
            s.fileName?.let { appendLine("File: $it") }
            appendLine("Rows: ${s.rows}   probes OK ${s.ok} / failed ${s.failed} / skipped ${s.skipped}")
            appendLine("Net events: ${s.events}")
            s.warning?.let { appendLine("⚠ $it") }
            s.lastRowSummary?.let { appendLine(); append(it) }
        }
    }

    private fun shareLastCsv() {
        val file = CsvLogger.latestFile(this)
        if (file == null) { toast("No CSV yet"); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
