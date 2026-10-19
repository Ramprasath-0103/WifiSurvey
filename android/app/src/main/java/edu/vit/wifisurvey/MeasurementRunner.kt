package edu.vit.wifisurvey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The eight stage sequence from Section 4.2 of the plan. One button press,
 * roughly ninety seconds, no interaction in between.
 */
class MeasurementRunner(
    private val ctx: Context,
    private val wifi: WifiManager,
    private val cfg: Config
) {
    data class Config(
        val downloadUrl: String,
        val uploadUrl: String,
        val gatewayIp: String,
        val idlePings: Int = 50,
        val recoveryPings: Int = 20,
        val transferSeconds: Long = 15
    )

    data class ScanSnapshot(val neighbours: List<NeighbourAp>, val servingLoad: BssLoad?, val fresh: Boolean)

    suspend fun run(
        sessionUuid: String, windowLabel: String, point: RefPoint, repetition: Int,
        progress: (Int, String) -> Unit
    ): Measurement = withContext(Dispatchers.IO) {

        progress(1, "Scanning for access points")
        val pre = scan()

        progress(2, "Reading the current connection")
        @Suppress("DEPRECATION") val info = wifi.connectionInfo
        val freq = info?.frequency
        val serving = pre.neighbours.firstOrNull { it.isServing }

        progress(3, "Idle latency, ${cfg.idlePings} pings to ${cfg.gatewayIp}")
        val idle = PingRunner.run(cfg.gatewayIp, cfg.idlePings)

        progress(4, "Download test, ${cfg.transferSeconds}s")
        var loaded: PingRunner.Result? = null
        val dl = coroutineScope {
            // Stage 5 runs concurrently with stage 4: latency while the link is busy.
            val pinger = async {
                delay(1500)                       // let the transfer ramp up first
                PingRunner.run(cfg.gatewayIp, 40)
            }
            val d = async { ThroughputRunner.download(cfg.downloadUrl, cfg.transferSeconds * 1000) }
            val r = d.await()
            loaded = pinger.await()
            r
        }

        progress(6, "Upload test, ${cfg.transferSeconds}s")
        val ul = ThroughputRunner.upload(cfg.uploadUrl, cfg.transferSeconds * 1000)

        progress(7, "Recovery ping")
        val recovery = PingRunner.run(cfg.gatewayIp, cfg.recoveryPings)

        progress(8, "Second scan, after load")
        val post = scan()

        Measurement(
            uuid = UUID.randomUUID().toString(),
            sessionUuid = sessionUuid,
            windowLabel = windowLabel,
            pointLabel = point.label,
            repetition = repetition,
            takenAtIso = MeasurementStore.iso(Date()),
            ssid = serving?.ssid, bssid = serving?.bssid,
            frequencyMhz = freq, channel = freq?.let { RadioMath.channelOf(it) },
            band = freq?.let { RadioMath.bandOf(it) },
            rssiDbm = info?.rssi ?: serving?.rssiDbm,
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info?.txLinkSpeedMbps else null,
            rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= 29) info?.rxLinkSpeedMbps else null,
            dlMbps = dl.mbps, ulMbps = ul.mbps,
            dlTargetHost = runCatching { java.net.URL(cfg.downloadUrl).host }.getOrNull(),
            idleLatencyMs = idle.stats?.medianMs,
            idleLatencyP95Ms = idle.stats?.p95Ms,
            loadedLatencyMs = loaded?.stats?.medianMs,
            recoveryLatencyMs = recovery.stats?.medianMs,
            jitterMs = idle.stats?.jitterMs,
            packetLossPct = idle.stats?.lossPct,
            gatewayIp = cfg.gatewayIp,
            latencyMethod = idle.method,
            bssLoadPre = pre.servingLoad, bssLoadPost = post.servingLoad,
            neighbours = pre.neighbours,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidSdk = Build.VERSION.SDK_INT,
            isControl = point.isControl,
            note = listOfNotNull(dl.error?.let { "download: $it" }, ul.error?.let { "upload: $it" })
                .joinToString("; ").ifBlank { null }
        )
    }

    /** Starts a scan and waits for the broadcast rather than trusting cached results. */
    private suspend fun scan(): ScanSnapshot {
        val fresh = awaitScan()
        @Suppress("DEPRECATION") val results = try { wifi.scanResults } catch (e: SecurityException) { emptyList() }
        @Suppress("DEPRECATION") val servingBssid = wifi.connectionInfo?.bssid

        val neighbours = results.map { r ->
            NeighbourAp(
                bssid = r.BSSID,
                ssid = @Suppress("DEPRECATION") r.SSID.ifBlank { null },
                channel = RadioMath.channelOf(r.frequency),
                frequencyMhz = r.frequency,
                rssiDbm = r.level,
                isServing = r.BSSID.equals(servingBssid, ignoreCase = true),
                bssLoad = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WifiScanner.bssLoadOf(r) else null
            )
        }.sortedByDescending { it.rssiDbm }

        return ScanSnapshot(neighbours, neighbours.firstOrNull { it.isServing }?.bssLoad, fresh)
    }

    private suspend fun awaitScan(): Boolean = withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine<Boolean> { cont ->
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    runCatching { ctx.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(i?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false)
                }
            }
            ctx.registerReceiver(rx, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            cont.invokeOnCancellation { runCatching { ctx.unregisterReceiver(rx) } }
            @Suppress("DEPRECATION")
            if (!wifi.startScan() && cont.isActive) {
                // Throttled. Cached results are still returned, flagged as not fresh.
                runCatching { ctx.unregisterReceiver(rx) }
                cont.resume(false)
            }
        }
    } ?: false
}
