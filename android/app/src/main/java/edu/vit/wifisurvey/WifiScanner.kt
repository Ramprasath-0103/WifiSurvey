package edu.vit.wifisurvey

import android.net.wifi.ScanResult
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Android-facing glue. Everything here needs a device; the arithmetic it delegates
 * to lives in BssLoadParser and is unit-tested on the JVM.
 */
object WifiScanner {

    /**
     * Pull the BSS Load element out of a scan result.
     *
     * getInformationElements() is API 30 (Android 11). Below that the element is
     * unreachable and the whole medium layer of Track B disappears - which is why
     * minSdk is 30 and the app refuses to run lower rather than degrading silently.
     *
     * Returns null when the AP simply does not advertise the element. That is common:
     * BSS Load is optional and only present when the AP supports QoS. Week 0 Check 2
     * establishes whether to expect it, but individual APs may still omit it.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun bssLoadOf(scan: ScanResult): BssLoad? {
        val ie = scan.informationElements
            ?.firstOrNull { it.id == BssLoadParser.ELEMENT_ID_BSS_LOAD }
            ?: return null

        // getBytes() hands back a read-only buffer whose position we must not disturb.
        val buf = ie.bytes.duplicate()
        val body = ByteArray(buf.remaining())
        buf.get(body)
        return BssLoadParser.parseBody(body)
    }
}
