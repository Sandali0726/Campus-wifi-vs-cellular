package lk.ac.mrt.netmeasure

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends rows to one CSV file per run and flushes after every row, so a crash or a killed
 * service loses at most the row being written.
 *
 * Location: app-specific external storage, no storage permission needed:
 *   /sdcard/Android/data/lk.ac.mrt.netmeasure/files/runs/
 * Pull with:  adb pull /sdcard/Android/data/lk.ac.mrt.netmeasure/files/runs/ data/raw/
 */
class CsvLogger private constructor(val file: File) {

    private val writer = BufferedWriter(FileWriter(file, true))

    init {
        if (file.length() == 0L) { writer.write(MeasurementRow.HEADER); writer.newLine(); writer.flush() }
    }

    @Synchronized
    fun append(row: MeasurementRow) {
        writer.write(row.toCsvLine())
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun close() = runCatching { writer.close() }

    companion object {
        fun runsDir(ctx: Context): File =
            File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "runs").apply { mkdirs() }

        /** e.g. A_WIFI_OFFPEAK_walk1_20261124-081502.csv */
        fun fileName(cfg: RunConfig, now: Date = Date()): String {
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
            return "${cfg.phoneId}_${cfg.netRole}_${cfg.timeSlot}_walk${cfg.walkId}_$ts.csv"
        }

        fun create(ctx: Context, cfg: RunConfig) = CsvLogger(File(runsDir(ctx), fileName(cfg)))

        fun latestFile(ctx: Context): File? =
            runsDir(ctx).listFiles { f -> f.name.endsWith(".csv") }?.maxByOrNull { it.lastModified() }
    }
}
