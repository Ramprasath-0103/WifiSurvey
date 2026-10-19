package edu.vit.wifisurvey

import org.junit.Assert.*
import org.junit.Test

class ThroughputMathTest {

    @Test fun `megabits per second from bytes and milliseconds`() {
        // 1,000,000 bytes in 1 second = 8 Mbps
        assertEquals(8.0, ThroughputMath.mbps(1_000_000, 1000)!!, 0.001)
        // 12.5 MB in 10 s = 10 Mbps
        assertEquals(10.0, ThroughputMath.mbps(12_500_000, 10_000)!!, 0.001)
    }

    @Test fun `zero or negative inputs return null instead of dividing by zero`() {
        assertNull(ThroughputMath.mbps(1000, 0))
        assertNull(ThroughputMath.mbps(0, 1000))
        assertNull(ThroughputMath.mbps(1000, -5))
    }

    @Test fun `warm up bytes are excluded from the rate`() {
        // 10 MB in 10 s overall, but 1 MB of that went in the first 2 s.
        // Steady state is 9 MB over 8 s = 9 Mbps, not the 8 Mbps the whole run suggests.
        val r = ThroughputMath.steadyStateMbps(10_000_000, 10_000, 1_000_000, 2_000)!!
        assertEquals(9.0, r, 0.001)
        val naive = ThroughputMath.mbps(10_000_000, 10_000)!!
        assertTrue("discarding slow start should raise the figure", r > naive)
    }

    @Test fun `transfers finishing inside the warm up window still report something`() {
        val r = ThroughputMath.steadyStateMbps(500_000, 1_500, 500_000, 1_500)
        assertNotNull(r)
        assertEquals(ThroughputMath.mbps(500_000, 1_500)!!, r!!, 0.001)
    }
}
