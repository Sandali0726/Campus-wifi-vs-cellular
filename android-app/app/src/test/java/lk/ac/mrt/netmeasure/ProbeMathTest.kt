package lk.ac.mrt.netmeasure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeMathTest {
    @Test fun medianOdd() = assertEquals(40.0, ProbeMath.median(listOf(120.0, 40.0, 35.0))!!, 1e-9)
    @Test fun medianEven() = assertEquals(37.5, ProbeMath.median(listOf(40.0, 35.0))!!, 1e-9)
    @Test fun medianEmpty() = assertNull(ProbeMath.median(emptyList()))
    @Test fun mean() = assertEquals(65.0, ProbeMath.mean(listOf(120.0, 40.0, 35.0))!!, 1e-9)

    @Test fun mbps() {
        // 256 000 B in 1000 ms = 2 048 000 bit/s = 2.048 Mbps
        assertEquals(2.048, ProbeMath.mbps(256_000, 1000.0)!!, 1e-9)
        assertNull(ProbeMath.mbps(256_000, 0.0))
    }

    @Test fun resultOkAndDerived() {
        val r = ProbeResult(listOf(30.0, 10.0, 20.0), 256_000, 50.0, 500.0, null)
        assertTrue(r.ok)
        assertEquals(20.0, r.latencyMedianMs!!, 1e-9)
        assertEquals(4.096, r.dlMbps!!, 1e-9)
    }
}
