package edu.vit.wifisurvey

/**
 * BSS Load, from the QBSS Load information element (ID 11) inside an 802.11 beacon.
 *
 * This is the medium-level layer of the project. Section 3.1 of the plan describes
 * the degraded fallback if access points do not advertise it.
 */
data class BssLoad(
    /** Devices currently associated to this AP. */
    val stationCount: Int,
    /** Raw channel utilisation, 0..255, as carried in the beacon. */
    val channelUtil255: Int,
    /** Available admission capacity, in units of 32 microseconds per second. */
    val admissionCapacity: Int
) {
    /** Channel utilisation as a percentage. The classification matrix in 7.2 uses this. */
    val channelUtilPct: Double get() = channelUtil255 * 100.0 / 255.0

    override fun toString() =
        "stations=%d util=%d/255 (%.1f%%) admission=%d"
            .format(stationCount, channelUtil255, channelUtilPct, admissionCapacity)
}

/**
 * Pure, framework-free parsing of the BSS Load element body.
 *
 * Deliberately free of any android.* import so it can be unit-tested on the JVM
 * without an emulator. The Android-facing wrapper lives in WifiScanner.kt.
 *
 * Element body layout (5 bytes, IEEE 802.11):
 *   [0..1]  station count           uint16, little-endian
 *   [2]     channel utilisation     uint8, 0..255
 *   [3..4]  available admission cap uint16, little-endian
 */
object BssLoadParser {

    const val ELEMENT_ID_BSS_LOAD = 11
    const val BODY_LENGTH = 5

    /**
     * @param body the element body WITHOUT the leading id/length octets, which is
     *             what Android's ScanResult.InformationElement.getBytes() gives.
     * @return parsed load, or null if the body is too short to be a BSS Load element.
     */
    fun parseBody(body: ByteArray?): BssLoad? {
        if (body == null || body.size < BODY_LENGTH) return null
        val u = { i: Int -> body[i].toInt() and 0xFF }        // unsigned byte
        return BssLoad(
            stationCount      = u(0) or (u(1) shl 8),
            channelUtil255    = u(2),
            admissionCapacity = u(3) or (u(4) shl 8)
        )
    }
}
