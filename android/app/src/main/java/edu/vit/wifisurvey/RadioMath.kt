package edu.vit.wifisurvey

/**
 * Frequency arithmetic. Pure and framework-free so it is unit-testable on the JVM.
 * Feeds the band and channel columns of the measurements table, and the co-channel
 * neighbour analysis in Section 4.5.
 */
object RadioMath {

    /** Band label from the centre frequency, for the band column ('2.4' | '5' | '6'). */
    fun bandOf(frequencyMhz: Int): String? = when {
        frequencyMhz in 2401..2499 -> "2.4"
        frequencyMhz in 5150..5895 -> "5"
        frequencyMhz in 5925..7125 -> "6"
        else                       -> null      // unknown / not a Wi-Fi frequency
    }

    /** IEEE channel number from the centre frequency, or -1 if not recognised. */
    fun channelOf(frequencyMhz: Int): Int = when {
        frequencyMhz == 2484        -> 14
        frequencyMhz in 2412..2472  -> (frequencyMhz - 2407) / 5
        frequencyMhz in 5160..5895  -> (frequencyMhz - 5000) / 5
        frequencyMhz in 5955..7115  -> (frequencyMhz - 5950) / 5
        else                        -> -1
    }

    /**
     * RSSI band per the thresholds table in Section 7.1.
     * These are conventional operational thresholds, not standardised values -
     * the report must say so and show the scatter with the boundary drawn on it.
     */
    fun rssiCategory(rssiDbm: Int): String = when {
        rssiDbm >= -67 -> "adequate"
        rssiDbm >= -75 -> "marginal"
        else           -> "weak"
    }

    /** Channel-utilisation band per Section 7.1. */
    fun utilisationCategory(pct: Double): String = when {
        pct < 30.0 -> "light"
        pct <= 50.0 -> "moderate"
        else       -> "contended"
    }
}
