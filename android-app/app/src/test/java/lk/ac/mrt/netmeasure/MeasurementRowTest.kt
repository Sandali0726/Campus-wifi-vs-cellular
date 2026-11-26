package lk.ac.mrt.netmeasure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MeasurementRowTest {

    private val base = MeasurementRow(
        epochMs = 1_795_000_000_000, elapsedMs = 12_345,
        phoneId = "A", netRole = "WIFI", timeSlot = "PEAK", walkId = 2,
        rowType = "PROBE", trigger = "TIMER", waypoint = "P3",
        activeTransport = "WIFI", wifiRssi = -61,
    )

    @Test fun columnCountMatchesHeader() {
        val headerCols = MeasurementRow.HEADER.split(",").size
        val row = base.withProbe(ProbeResult(listOf(10.0, 20.0, 30.0), 256_000, 5.0, 100.0, null))
        assertEquals(headerCols, row.toCsvLine().split(",").size)
    }

    @Test fun probeFieldsFilled() {
        val line = base.withProbe(ProbeResult(listOf(10.0, 20.0, 30.0), 256_000, 5.0, 100.0, null)).toCsvLine()
        assertTrue(line.contains(",OK,"))
        assertTrue(line.contains(",20.00,20.00,")) // median, mean
        assertTrue(line.contains(",20.480,"))      // 256 000 B in 100 ms = 20.48 Mbps
    }

    @Test fun failedProbeKeepsError() {
        val line = base.withProbe(ProbeResult(listOf(10.0, null, 30.0), null, null, null, "ping2:SocketTimeoutException")).toCsvLine()
        assertTrue(line.contains(",FAILED,ping2:SocketTimeoutException,"))
        assertTrue(line.contains(",10.00,,30.00,20.00,20.00,")) // ping1=10, ping2=empty, ping3=30, median=20, mean=20
    }

    @Test fun firstPingFailureDoesNotShiftColumns() {
        val row = base.withProbe(ProbeResult(listOf(null, 25.0, 35.0), 256_000, 5.0, 100.0, "ping1:SocketTimeoutException"))
        assertEquals(null, row.ping1Ms)
        assertEquals(25.0, row.ping2Ms!!, 1e-9)
        assertEquals(35.0, row.ping3Ms!!, 1e-9)
        assertEquals(30.0, row.latencyMedianMs!!, 1e-9)
        assertEquals(30.0, row.latencyMeanMs!!, 1e-9)
    }

    @Test fun escaping() {
        assertEquals("\"a,b\"", MeasurementRow.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", MeasurementRow.escape("say \"hi\""))
        assertEquals("plain", MeasurementRow.escape("plain"))
    }

    @Test fun fileNamePattern() {
        val cfg = RunConfig("B", "CELL", "OFFPEAK", 3, "https://x")
        assertTrue(CsvLogger.fileName(cfg, Date(0)).matches(Regex("B_CELL_OFFPEAK_walk3_\\d{8}-\\d{6}\\.csv")))
    }
}
