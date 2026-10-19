package edu.vit.wifisurvey

import org.junit.Assert.*
import org.junit.Test

class PingOutputParserTest {

    private val androidPing = """
PING 192.168.1.1 (192.168.1.1) 56(84) bytes of data.
64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=12.3 ms
64 bytes from 192.168.1.1: icmp_seq=2 ttl=64 time=8.1 ms
64 bytes from 192.168.1.1: icmp_seq=3 ttl=64 time=25.7 ms
64 bytes from 192.168.1.1: icmp_seq=4 ttl=64 time=98.2 ms

--- 192.168.1.1 ping statistics ---
5 packets transmitted, 4 received, 20% packet loss, time 4005ms
rtt min/avg/max/mdev = 8.113/36.075/98.220/35.331 ms
""".trimIndent()

    private val macPing = """
PING 10.0.0.1 (10.0.0.1): 56 data bytes
64 bytes from 10.0.0.1: icmp_seq=0 ttl=64 time=4.521 ms
64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=5.113 ms
--- 10.0.0.1 ping statistics ---
2 packets transmitted, 2 packets received, 0.0% packet loss
round-trip min/avg/max/stddev = 4.521/4.817/5.113/0.296 ms
""".trimIndent()

    private val totalLoss = """
PING 10.0.0.9 (10.0.0.9) 56(84) bytes of data.

--- 10.0.0.9 ping statistics ---
10 packets transmitted, 0 received, 100% packet loss, time 9200ms
""".trimIndent()

    @Test fun `parses android ping output`() {
        val s = PingOutputParser.parse(androidPing)!!
        assertEquals(5, s.sent)
        assertEquals(4, s.received)
        assertEquals(20.0, s.lossPct, 0.01)
        assertEquals(4, s.samples.size)
        assertEquals(8.113, s.minMs!!, 0.001)
        assertEquals(98.220, s.maxMs!!, 0.001)
        assertEquals(35.331, s.jitterMs!!, 0.001)   // mdev preferred when present
        assertTrue(s.usable)
    }

    @Test fun `parses bsd style output from macOS`() {
        val s = PingOutputParser.parse(macPing)!!
        assertEquals(2, s.sent)
        assertEquals(2, s.received)
        assertEquals(0.0, s.lossPct, 0.01)
        assertEquals(0.296, s.jitterMs!!, 0.001)    // stddev wording also accepted
    }

    @Test fun `total packet loss does not crash and is reported honestly`() {
        val s = PingOutputParser.parse(totalLoss)!!
        assertEquals(10, s.sent)
        assertEquals(0, s.received)
        assertEquals(100.0, s.lossPct, 0.01)
        assertNull(s.medianMs)          // there is genuinely no latency to report
        assertFalse(s.usable)           // caller must not treat this as a reading
    }

    @Test fun `garbage and empty input return null rather than throwing`() {
        assertNull(PingOutputParser.parse(null))
        assertNull(PingOutputParser.parse(""))
        assertNull(PingOutputParser.parse("ping: socket: Operation not permitted"))
    }

    @Test fun `median and p95 come from the samples`() {
        val s = PingOutputParser.parse(androidPing)!!
        // sorted: 8.1, 12.3, 25.7, 98.2  -> nearest-rank median is the 2nd value
        assertEquals(12.3, s.medianMs!!, 0.001)
        assertEquals(98.2, s.p95Ms!!, 0.001)
    }

    @Test fun `percentile handles single and empty lists`() {
        assertNull(PingOutputParser.percentile(emptyList(), 50.0))
        assertEquals(7.0, PingOutputParser.percentile(listOf(7.0), 95.0)!!, 0.001)
    }

    @Test fun `jitter falls back to mean absolute deviation when mdev is absent`() {
        val noRttLine = """
64 bytes from 1.1.1.1: icmp_seq=1 ttl=64 time=10.0 ms
64 bytes from 1.1.1.1: icmp_seq=2 ttl=64 time=20.0 ms
64 bytes from 1.1.1.1: icmp_seq=3 ttl=64 time=15.0 ms
3 packets transmitted, 3 received, 0% packet loss, time 2000ms
""".trimIndent()
        val s = PingOutputParser.parse(noRttLine)!!
        // |20-10| = 10, |15-20| = 5  -> mean 7.5
        assertEquals(7.5, s.jitterMs!!, 0.001)
    }

    @Test fun `sub millisecond replies using time less-than notation are captured`() {
        val s = PingOutputParser.parse(
            "64 bytes from 1.1.1.1: icmp_seq=1 ttl=64 time<1 ms\n" +
            "1 packets transmitted, 1 received, 0% packet loss")!!
        assertEquals(1, s.samples.size)
    }
}
