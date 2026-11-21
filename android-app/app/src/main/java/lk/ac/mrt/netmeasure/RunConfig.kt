package lk.ac.mrt.netmeasure

import android.content.Context
import android.content.Intent

/** Settings for one walk, chosen on the main screen and passed to the service. */
data class RunConfig(
    val phoneId: String,      // "A" or "B" (physical handset)
    val netRole: String,      // "WIFI" or "CELL" (which network this phone measures on this walk)
    val timeSlot: String,     // "OFFPEAK" or "PEAK"
    val walkId: Int,          // 1..3
    val serverUrl: String,    // fixed ngrok URL, e.g. https://xyz.ngrok-free.app
) {
    fun putInto(intent: Intent): Intent = intent
        .putExtra(K_PHONE, phoneId)
        .putExtra(K_ROLE, netRole)
        .putExtra(K_SLOT, timeSlot)
        .putExtra(K_WALK, walkId)
        .putExtra(K_URL, serverUrl)

    fun save(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(K_PHONE, phoneId).putString(K_ROLE, netRole)
            .putString(K_SLOT, timeSlot).putInt(K_WALK, walkId)
            .putString(K_URL, serverUrl).apply()
    }

    companion object {
        private const val PREFS = "run_config"
        private const val K_PHONE = "phone"
        private const val K_ROLE = "role"
        private const val K_SLOT = "slot"
        private const val K_WALK = "walk"
        private const val K_URL = "url"

        fun from(intent: Intent): RunConfig? {
            val url = intent.getStringExtra(K_URL) ?: return null
            return RunConfig(
                phoneId = intent.getStringExtra(K_PHONE) ?: "A",
                netRole = intent.getStringExtra(K_ROLE) ?: "WIFI",
                timeSlot = intent.getStringExtra(K_SLOT) ?: "OFFPEAK",
                walkId = intent.getIntExtra(K_WALK, 1),
                serverUrl = url,
            )
        }

        fun load(ctx: Context): RunConfig {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return RunConfig(
                phoneId = p.getString(K_PHONE, "A")!!,
                netRole = p.getString(K_ROLE, "WIFI")!!,
                timeSlot = p.getString(K_SLOT, "OFFPEAK")!!,
                walkId = p.getInt(K_WALK, 1),
                serverUrl = p.getString(K_URL, "")!!,
            )
        }
    }
}
