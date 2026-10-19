package edu.vit.wifisurvey

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Latency against the default gateway.
 *
 * Android apps cannot open raw ICMP sockets without root, and
 * InetAddress.isReachable() silently falls back to a TCP probe that does not
 * produce real round trip times. So we shell out to the system ping binary and
 * parse it, with a TCP connect timer as the fallback when ICMP is blocked.
 */
object PingRunner {

    data class Result(val stats: PingStats?, val method: String)

    fun run(host: String, count: Int, intervalSec: Double = 0.2, timeoutSec: Long = 40): Result {
        icmp(host, count, intervalSec, timeoutSec)?.let { if (it.usable) return Result(it, "icmp") }
        return Result(tcpConnectRtt(host, count), "tcp_connect")
    }

    private fun icmp(host: String, count: Int, intervalSec: Double, timeoutSec: Long): PingStats? =
        try {
            val p = ProcessBuilder(
                "/system/bin/ping", "-c", count.toString(), "-i", intervalSec.toString(), host
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) p.destroyForcibly()
            PingOutputParser.parse(out)
        } catch (e: Exception) {
            null
        }

    /**
     * Fallback when ICMP is filtered. Timing a TCP connect to a closed port still
     * yields a valid round trip, because the SYN gets an immediate RST. Noisier
     * than ICMP but entirely usable, and we record which method produced the row.
     */
    private fun tcpConnectRtt(host: String, count: Int, port: Int = 9): PingStats? {
        val samples = mutableListOf<Double>()
        var failures = 0
        repeat(count) {
            val t0 = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
                samples += (System.nanoTime() - t0) / 1_000_000.0
            } catch (e: IOException) {
                // Connection refused still proves the round trip happened.
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                if (ms < 1500) samples += ms else failures++
            } catch (e: Exception) {
                failures++
            }
            Thread.sleep(150)
        }
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        return PingStats(
            sent = count, received = samples.size,
            lossPct = failures * 100.0 / count,
            medianMs = PingOutputParser.percentile(sorted, 50.0),
            p95Ms = PingOutputParser.percentile(sorted, 95.0),
            minMs = sorted.first(), maxMs = sorted.last(),
            jitterMs = PingOutputParser.meanAbsDeviation(samples),
            samples = samples
        )
    }
}
