package edu.vit.wifisurvey

import kotlin.math.roundToInt

/**
 * Result of one ping run against the gateway.
 * All times in milliseconds. Null fields mean "could not be determined".
 */
data class PingStats(
    val sent: Int,
    val received: Int,
    val lossPct: Double,
    val medianMs: Double?,
    val p95Ms: Double?,
    val minMs: Double?,
    val maxMs: Double?,
    val jitterMs: Double?,       // mdev from ping, or computed from samples
    val samples: List<Double>
) {
    val usable: Boolean get() = received > 0 && medianMs != null
}

/**
 * Parses the text output of the system ping binary.
 *
 * Pure text to numbers, no android imports, so it is unit-testable on a JVM.
 * This matters because ping output varies between the BSD-ish ping on a Mac,
 * GNU ping on Linux and the toybox ping Android ships, and getting it wrong
 * is the kind of bug that only shows up while you are standing in a corridor.
 */
object PingOutputParser {

    private val TIME_RE   = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE)
    private val SUMMARY_RE = Regex(
        """(\d+)\s+packets transmitted,\s*(\d+)\s*(?:packets\s+)?received""", RegexOption.IGNORE_CASE)
    private val LOSS_RE   = Regex("""([0-9]+(?:\.[0-9]+)?)%\s*packet loss""", RegexOption.IGNORE_CASE)
    // "rtt min/avg/max/mdev = 8.113/21.446/98.220/12.331 ms"  (Linux / Android)
    // "round-trip min/avg/max/stddev = 8.1/21.4/98.2/12.3 ms" (BSD / macOS)
    private val RTT_RE = Regex(
        """(?:rtt|round-trip)\s+min/avg/max/(?:mdev|stddev)\s*=\s*""" +
        """([0-9.]+)/([0-9.]+)/([0-9.]+)/([0-9.]+)""", RegexOption.IGNORE_CASE)

    fun parse(output: String?): PingStats? {
        if (output.isNullOrBlank()) return null

        val samples = TIME_RE.findAll(output).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()

        val summary = SUMMARY_RE.find(output)
        val sent = summary?.groupValues?.get(1)?.toIntOrNull()
        val recv = summary?.groupValues?.get(2)?.toIntOrNull()

        // 100% loss produces a summary line but no rtt line and no samples at all.
        // That is a real, reportable outcome, not a parse failure.
        if (sent == null || recv == null) {
            if (samples.isEmpty()) return null
            return fromSamples(samples.size, samples.size, 0.0, samples, null)
        }

        val loss = LOSS_RE.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: if (sent > 0) (sent - recv) * 100.0 / sent else 0.0

        val rtt = RTT_RE.find(output)
        val mdev = rtt?.groupValues?.get(4)?.toDoubleOrNull()
        val rttMin = rtt?.groupValues?.get(1)?.toDoubleOrNull()
        val rttMax = rtt?.groupValues?.get(3)?.toDoubleOrNull()

        if (samples.isEmpty()) {
            return PingStats(sent, recv, loss, null, null, rttMin, rttMax, mdev, emptyList())
        }
        return fromSamples(sent, recv, loss, samples, mdev).copy(
            minMs = rttMin ?: samples.min(),
            maxMs = rttMax ?: samples.max()
        )
    }

    private fun fromSamples(
        sent: Int, recv: Int, loss: Double, samples: List<Double>, mdev: Double?
    ): PingStats {
        val sorted = samples.sorted()
        return PingStats(
            sent = sent,
            received = recv,
            lossPct = loss,
            medianMs = percentile(sorted, 50.0),
            p95Ms = percentile(sorted, 95.0),
            minMs = sorted.first(),
            maxMs = sorted.last(),
            jitterMs = mdev ?: meanAbsDeviation(samples),
            samples = samples
        )
    }

    /** Nearest-rank percentile. Simple, and defensible in a viva. */
    fun percentile(sortedAsc: List<Double>, pct: Double): Double? {
        if (sortedAsc.isEmpty()) return null
        if (sortedAsc.size == 1) return sortedAsc[0]
        val rank = (pct / 100.0 * sortedAsc.size).roundToInt().coerceIn(1, sortedAsc.size)
        return sortedAsc[rank - 1]
    }

    /**
     * Fallback jitter when ping gives no mdev: mean absolute difference between
     * consecutive round trips. This is the inter-arrival variation definition,
     * which is what matters for a call sounding choppy.
     */
    fun meanAbsDeviation(samples: List<Double>): Double? {
        if (samples.size < 2) return null
        var total = 0.0
        for (i in 1 until samples.size) total += kotlin.math.abs(samples[i] - samples[i - 1])
        return total / (samples.size - 1)
    }
}
