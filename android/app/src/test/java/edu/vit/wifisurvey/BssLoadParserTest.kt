package edu.vit.wifisurvey

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the BSS Load element parser and radio arithmetic.
 * These run on the JVM - no device or emulator needed:  ./gradlew :app:test
 */
class BssLoadParserTest {

    /** Element body as an AP encodes it: LE uint16, uint8, LE uint16. */
    private fun body(stations: Int, util: Int, admission: Int) = byteArrayOf(
        (stations and 0xFF).toByte(), ((stations shr 8) and 0xFF).toByte(),
        (util and 0xFF).toByte(),
        (admission and 0xFF).toByte(), ((admission shr 8) and 0xFF).toByte()
    )

    @Test fun `parses the worked example from the plan`() {
        val l = BssLoadParser.parseBody(body(27, 118, 15625))!!
        assertEquals(27, l.stationCount)
        assertEquals(118, l.channelUtil255)
        assertEquals(15625, l.admissionCapacity)
        assertEquals(46.27, l.channelUtilPct, 0.01)
    }

    @Test fun `utilisation boundaries map to 0 and 100 percent`() {
        assertEquals(0.0,   BssLoadParser.parseBody(body(0, 0, 0))!!.channelUtilPct, 0.01)
        assertEquals(100.0, BssLoadParser.parseBody(body(0, 255, 0))!!.channelUtilPct, 0.01)
    }

    @Test fun `bytes above 127 are treated as unsigned`() {
        assertEquals(200,   BssLoadParser.parseBody(body(0, 200, 0))!!.channelUtil255)
        assertEquals(300,   BssLoadParser.parseBody(body(300, 0, 0))!!.stationCount)
        assertEquals(65535, BssLoadParser.parseBody(body(65535, 0, 0))!!.stationCount)
    }

    @Test fun `multi-byte fields are little-endian`() {
        // 0x34,0x12 is 4660 little-endian, 13330 big-endian
        assertEquals(4660,
            BssLoadParser.parseBody(byteArrayOf(0x34, 0x12, 0, 0, 0))!!.stationCount)
    }

    @Test fun `malformed bodies return null rather than throwing`() {
        assertNull(BssLoadParser.parseBody(null))
        assertNull(BssLoadParser.parseBody(byteArrayOf()))
        assertNull(BssLoadParser.parseBody(byteArrayOf(1, 2, 3, 4)))
    }

    @Test fun `padded bodies still parse`() {
        assertEquals(BssLoad(27, 118, 15625),
            BssLoadParser.parseBody(byteArrayOf(0x1B, 0, 0x76, 0x09, 0x3D, 0x00)))
    }

    @Test fun `channel numbers derive from frequency`() {
        assertEquals(1,   RadioMath.channelOf(2412))
        assertEquals(6,   RadioMath.channelOf(2437))
        assertEquals(11,  RadioMath.channelOf(2462))
        assertEquals(14,  RadioMath.channelOf(2484))
        assertEquals(36,  RadioMath.channelOf(5180))
        assertEquals(149, RadioMath.channelOf(5745))
        assertEquals(-1,  RadioMath.channelOf(1234))
    }

    @Test fun `band labels derive from frequency`() {
        assertEquals("2.4", RadioMath.bandOf(2412))
        assertEquals("5",   RadioMath.bandOf(5180))
        assertEquals("6",   RadioMath.bandOf(6115))
        assertNull(RadioMath.bandOf(1234))
    }

    @Test fun `classification thresholds match section 7 point 1`() {
        assertEquals("adequate",  RadioMath.rssiCategory(-67))
        assertEquals("marginal",  RadioMath.rssiCategory(-68))
        assertEquals("marginal",  RadioMath.rssiCategory(-75))
        assertEquals("weak",      RadioMath.rssiCategory(-76))
        assertEquals("light",     RadioMath.utilisationCategory(29.9))
        assertEquals("moderate",  RadioMath.utilisationCategory(30.0))
        assertEquals("moderate",  RadioMath.utilisationCategory(50.0))
        assertEquals("contended", RadioMath.utilisationCategory(50.1))
    }
}
