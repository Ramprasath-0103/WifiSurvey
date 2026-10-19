package edu.vit.wifisurvey

import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Download and upload speed against one fixed public endpoint.
 *
 * Revision 2 of the plan: with no wired host available on campus, throughput is
 * measured end to end rather than confined to the wireless hop. The same endpoint
 * is used for the whole study and a control point is measured every session, so
 * campus uplink congestion can be told apart from a genuinely bad survey point.
 */
object ThroughputRunner {

    data class Result(val mbps: Double?, val bytes: Long, val millis: Long, val error: String?)

    fun download(url: String, capMillis: Long = 15_000, capBytes: Long = 50L * 1024 * 1024): Result {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Cache-Control", "no-store")
            }
            val start = System.currentTimeMillis()
            var total = 0L
            var warmBytes = 0L
            var warmMillis = 0L
            val buf = ByteArray(32 * 1024)
            conn.inputStream.use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    val elapsed = System.currentTimeMillis() - start
                    if (warmMillis == 0L && elapsed >= ThroughputMath.WARMUP_MS) {
                        warmMillis = elapsed; warmBytes = total
                    }
                    if (elapsed >= capMillis || total >= capBytes) break
                }
            }
            val elapsed = System.currentTimeMillis() - start
            Result(ThroughputMath.steadyStateMbps(total, elapsed, warmBytes, warmMillis),
                   total, elapsed, null)
        } catch (e: Exception) {
            Result(null, 0, 0, e.javaClass.simpleName + ": " + e.message)
        } finally {
            conn?.disconnect()
        }
    }

    fun upload(url: String, capMillis: Long = 15_000): Result {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000; readTimeout = 15_000
                setChunkedStreamingMode(64 * 1024)
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            val chunk = Random.nextBytes(64 * 1024)   // incompressible, so no cheating
            val start = System.currentTimeMillis()
            var total = 0L
            var warmBytes = 0L
            var warmMillis = 0L
            BufferedOutputStream(conn.outputStream).use { out ->
                while (true) {
                    out.write(chunk)
                    total += chunk.size
                    val elapsed = System.currentTimeMillis() - start
                    if (warmMillis == 0L && elapsed >= ThroughputMath.WARMUP_MS) {
                        warmMillis = elapsed; warmBytes = total
                    }
                    if (elapsed >= capMillis) break
                }
                out.flush()
            }
            val elapsed = System.currentTimeMillis() - start
            conn.responseCode
            Result(ThroughputMath.steadyStateMbps(total, elapsed, warmBytes, warmMillis),
                   total, elapsed, null)
        } catch (e: Exception) {
            Result(null, 0, 0, e.javaClass.simpleName + ": " + e.message)
        } finally {
            conn?.disconnect()
        }
    }
}
