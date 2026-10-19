package edu.vit.wifisurvey

/**
 * Throughput arithmetic, kept pure so it can be tested without a network.
 *
 * The warm-up discard matters: TCP starts slow and ramps up, so timing from the
 * very first byte understates the link. We ignore everything before the warm-up
 * mark and measure only the steady-state portion.
 */
object ThroughputMath {

    const val WARMUP_MS = 2000L

    /** Megabits per second from bytes moved over a span of milliseconds. */
    fun mbps(bytes: Long, millis: Long): Double? {
        if (millis <= 0 || bytes <= 0) return null
        return (bytes * 8.0) / (millis / 1000.0) / 1_000_000.0
    }

    /**
     * Steady-state throughput: subtract whatever was transferred during the
     * warm-up window, then rate the remainder over the remaining time.
     * Falls back to the whole transfer if it finished before warm-up ended.
     */
    fun steadyStateMbps(
        totalBytes: Long, totalMillis: Long,
        warmupBytes: Long, warmupMillis: Long
    ): Double? {
        val b = totalBytes - warmupBytes
        val t = totalMillis - warmupMillis
        return if (b > 0 && t > 0) mbps(b, t) else mbps(totalBytes, totalMillis)
    }
}
