package lk.ac.mrt.netmeasure

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Live state shared between the service (writer) and the screen (reader). */
object MeasurementState {

    data class Status(
        val running: Boolean = false,
        val fileName: String? = null,
        val rows: Int = 0,
        val ok: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val events: Int = 0,
        val waypoint: String? = null,
        val waypointTapEpochMs: Long = 0,
        val warning: String? = null,
        val lastRowSummary: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    fun update(f: (Status) -> Status) = _status.update(f)
    fun reset(s: Status) { _status.value = s }
}
