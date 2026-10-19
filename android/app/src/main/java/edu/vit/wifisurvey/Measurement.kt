package edu.vit.wifisurvey

import org.json.JSONArray
import org.json.JSONObject

data class NeighbourAp(
    val bssid: String, val ssid: String?, val channel: Int,
    val frequencyMhz: Int, val rssiDbm: Int, val isServing: Boolean,
    val bssLoad: BssLoad?
)

/** One complete run of the eight stage sequence at one reference point. */
data class Measurement(
    val uuid: String,
    val sessionUuid: String,
    val windowLabel: String,          // morning | evening
    val pointLabel: String,
    val repetition: Int,
    val takenAtIso: String,

    val ssid: String?, val bssid: String?,
    val frequencyMhz: Int?, val channel: Int?, val band: String?,
    val rssiDbm: Int?,
    val txLinkSpeedMbps: Int?, val rxLinkSpeedMbps: Int?,

    val dlMbps: Double?, val ulMbps: Double?, val dlTargetHost: String?,

    val idleLatencyMs: Double?, val idleLatencyP95Ms: Double?,
    val loadedLatencyMs: Double?, val recoveryLatencyMs: Double?,
    val jitterMs: Double?, val packetLossPct: Double?,
    val gatewayIp: String?, val latencyMethod: String?,

    val bssLoadPre: BssLoad?, val bssLoadPost: BssLoad?,
    val neighbours: List<NeighbourAp>,

    val deviceModel: String, val androidSdk: Int,
    val isControl: Boolean,
    val note: String? = null
) {
    fun toJson(): JSONObject {
        fun load(b: BssLoad?) = b?.let {
            JSONObject()
                .put("station_count", it.stationCount)
                .put("channel_util_255", it.channelUtil255)
                .put("channel_util_pct", String.format("%.2f", it.channelUtilPct).toDouble())
                .put("admission_capacity", it.admissionCapacity)
        }
        val nb = JSONArray()
        neighbours.forEach {
            nb.put(JSONObject()
                .put("bssid", it.bssid).put("ssid", it.ssid)
                .put("channel", it.channel).put("frequency_mhz", it.frequencyMhz)
                .put("rssi_dbm", it.rssiDbm).put("is_serving", it.isServing)
                .put("bss_load", load(it.bssLoad)))
        }
        return JSONObject()
            .put("uuid", uuid)
            .put("session_uuid", sessionUuid)
            .put("window_label", windowLabel)
            .put("point_label", pointLabel)
            .put("repetition", repetition)
            .put("taken_at", takenAtIso)
            .put("is_control", isControl)
            .put("ssid", ssid).put("bssid", bssid)
            .put("frequency_mhz", frequencyMhz).put("channel", channel).put("band", band)
            .put("rssi_dbm", rssiDbm)
            .put("tx_link_speed_mbps", txLinkSpeedMbps)
            .put("rx_link_speed_mbps", rxLinkSpeedMbps)
            .put("dl_mbps", dlMbps).put("ul_mbps", ulMbps)
            .put("dl_target_host", dlTargetHost)
            .put("idle_latency_ms", idleLatencyMs)
            .put("idle_latency_p95_ms", idleLatencyP95Ms)
            .put("loaded_latency_ms", loadedLatencyMs)
            .put("recovery_latency_ms", recoveryLatencyMs)
            .put("jitter_ms", jitterMs)
            .put("packet_loss_pct", packetLossPct)
            .put("gateway_ip", gatewayIp)
            .put("latency_method", latencyMethod)
            .put("bss_load_pre", load(bssLoadPre))
            .put("bss_load_post", load(bssLoadPost))
            .put("neighbours", nb)
            .put("device_model", deviceModel)
            .put("android_sdk", androidSdk)
            .put("note", note)
    }
}
