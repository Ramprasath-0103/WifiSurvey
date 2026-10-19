package edu.vit.wifisurvey

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var wifi: WifiManager
    private lateinit var store: MeasurementStore

    private lateinit var pointSpinner: Spinner
    private lateinit var windowSpinner: Spinner
    private lateinit var repSpinner: Spinner
    private lateinit var gatewayField: EditText
    private lateinit var runBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var diagBtn: Button
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var countLabel: TextView

    private var sessionUuid = UUID.randomUUID().toString()
    private var busy = false

    private val DL = "https://speed.cloudflare.com/__down?bytes=52428800"
    private val UL = "https://speed.cloudflare.com/__up"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        store = MeasurementStore(this)

        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 12f; setTextColor(Color.parseColor("#777777"))
            setPadding(0, 18, 0, 2)
        }

        pointSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, ReferencePoints.labels())
            setSelection(ReferencePoints.ALL.indexOfFirst { it.isControl })
        }
        windowSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("morning", "afternoon", "evening", "night"))
            // preselect whichever window the clock is currently in
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            setSelection(when { h < 12 -> 0; h < 17 -> 1; h < 22 -> 2; else -> 3 })
        }
        repSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, listOf("1", "2", "3"))
        }
        gatewayField = EditText(this).apply {
            setText(detectGateway() ?: "")
            hint = "gateway IP"
            textSize = 15f
        }
        runBtn = Button(this).apply {
            text = "RUN MEASUREMENT"
            textSize = 17f
            setOnClickListener { runMeasurement() }
        }
        diagBtn = Button(this).apply {
            text = "SELF CHECK"
            setOnClickListener { selfCheck() }
        }
        exportBtn = Button(this).apply {
            text = "EXPORT TO DOWNLOADS"
            setOnClickListener { export() }
        }
        status = TextView(this).apply { textSize = 14f; setPadding(0, 18, 0, 6) }
        result = TextView(this).apply {
            textSize = 12.5f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
        }
        countLabel = TextView(this).apply {
            textSize = 13f; setTextColor(Color.parseColor("#555555")); setPadding(0, 10, 0, 0)
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 48)
            addView(TextView(context).apply {
                text = "WiFi Survey"; textSize = 21f; setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = "session ${sessionUuid.take(8)}"
                textSize = 11f; setTextColor(Color.parseColor("#999999"))
            })
            addView(label("REFERENCE POINT")); addView(pointSpinner)
            addView(label("TIME WINDOW"));     addView(windowSpinner)
            addView(label("REPETITION"));      addView(repSpinner)
            addView(label("GATEWAY"));         addView(gatewayField)
            addView(runBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 36 })
            addView(diagBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 8 })
            addView(status)
            addView(result)
            addView(countLabel)
            addView(exportBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = 24 })
        }
        setContentView(ScrollView(this).apply { addView(col) })

        requestPermissions()
        refreshCount()
        status.text = "Ready. Stand at the point, hold the phone at chest height, press Run."
    }

    // ------------------------------------------------------------- plumbing

    private fun requestPermissions() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            need += Manifest.permission.NEARBY_WIFI_DEVICES
        val missing = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    /**
     * Find the default gateway.
     *
     * WifiManager.getDhcpInfo() is deprecated and returns zeros on recent Android,
     * so the real lookup goes through ConnectivityManager and the interface's
     * default route. DhcpInfo is kept only as a fallback for older devices.
     */
    private fun detectGateway(): String? = gatewayFromRoutes() ?: gatewayFromDhcp()

    private fun gatewayFromRoutes(): String? = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork)
        lp?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is java.net.Inet4Address }
            ?.gateway?.hostAddress
            ?: lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun gatewayFromDhcp(): String? = try {
        val gw = wifi.dhcpInfo?.gateway ?: 0
        if (gw == 0) null else
            "%d.%d.%d.%d".format(gw and 0xFF, gw shr 8 and 0xFF, gw shr 16 and 0xFF, gw shr 24 and 0xFF)
    } catch (e: Exception) { null }

    private fun locationOn(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onResume() {
        super.onResume()
        if (gatewayField.text.isNullOrBlank()) {
            detectGateway()?.let {
                gatewayField.setText(it)
                status.text = "Gateway detected: $it. Ready."
            }
        }
    }

    private fun refreshCount() {
        countLabel.text = "${store.count()} measurements stored on this phone"
    }

    // ------------------------------------------------------------- the run

    private fun runMeasurement() {
        if (busy) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) { requestPermissions(); return }
        if (!locationOn()) {
            status.text = "Location services are off. Scanning returns nothing until they are on."
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return
        }
        val gw = gatewayField.text.toString().trim()
        if (gw.isEmpty()) {
            val found = detectGateway()
            if (found != null) {
                gatewayField.setText(found)
                status.text = "Gateway detected: $found. Press Run again."
            } else {
                status.text = "Could not detect the gateway. Check you are on Wi-Fi, " +
                    "or type it in: Settings > Wi-Fi > your network > Router or Gateway."
            }
            return
        }

        val point = ReferencePoints.ALL[pointSpinner.selectedItemPosition]
        val window = windowSpinner.selectedItem as String
        val rep = (repSpinner.selectedItem as String).toInt()

        busy = true
        runBtn.isEnabled = false
        result.text = ""
        val runner = MeasurementRunner(this, wifi,
            MeasurementRunner.Config(downloadUrl = DL, uploadUrl = UL, gatewayIp = gw))

        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            try {
                val m = runner.run(sessionUuid, window, point, rep) { stage, msg ->
                    runOnUiThread { status.text = "Stage $stage of 8  ·  $msg" }
                }
                store.append(m)
                val secs = (System.currentTimeMillis() - started) / 1000
                status.text = "Done in ${secs}s. Saved."
                result.text = summarise(m)
            } catch (e: Exception) {
                status.text = "Failed: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                busy = false
                runBtn.isEnabled = true
                refreshCount()
            }
        }
    }

    private fun summarise(m: Measurement): String {
        fun f(d: Double?, u: String) = d?.let { "%.1f %s".format(it, u) } ?: "n/a"
        val load = m.bssLoadPre
        return buildString {
            appendLine("POINT       ${m.pointLabel}  (${m.windowLabel}, rep ${m.repetition})")
            appendLine("SSID        ${m.ssid ?: "?"}")
            appendLine("RSSI        ${m.rssiDbm ?: "?"} dBm   ch ${m.channel ?: "?"}  ${m.band ?: "?"} GHz")
            appendLine("LINK        tx ${m.txLinkSpeedMbps ?: "?"} / rx ${m.rxLinkSpeedMbps ?: "?"} Mbps")
            appendLine("")
            appendLine("DOWNLOAD    ${f(m.dlMbps, "Mbps")}")
            appendLine("UPLOAD      ${f(m.ulMbps, "Mbps")}")
            appendLine("LATENCY     idle ${f(m.idleLatencyMs, "ms")}  loaded ${f(m.loadedLatencyMs, "ms")}")
            val ratio = if (m.idleLatencyMs != null && m.loadedLatencyMs != null && m.idleLatencyMs > 0)
                "%.1fx".format(m.loadedLatencyMs / m.idleLatencyMs) else "n/a"
            appendLine("  ratio     $ratio   (over 5x means heavy queueing)")
            appendLine("JITTER      ${f(m.jitterMs, "ms")}   LOSS ${f(m.packetLossPct, "%")}")
            appendLine("  method    ${m.latencyMethod}")
            appendLine("")
            if (load != null) {
                appendLine("BSS LOAD    ${load.stationCount} stations, " +
                           "%.1f%% channel use".format(load.channelUtilPct))
                appendLine("  verdict   ${RadioMath.utilisationCategory(load.channelUtilPct)} medium, " +
                           "${RadioMath.rssiCategory(m.rssiDbm ?: -100)} signal")
            } else {
                appendLine("BSS LOAD    serving AP does not advertise it")
            }
            appendLine("NEIGHBOURS  ${m.neighbours.size} APs visible, " +
                       "${m.neighbours.count { it.bssLoad != null }} with BSS Load")
            m.note?.let { appendLine(""); appendLine("NOTE        $it") }
        }
    }

    /**
     * Why am I seeing no networks?
     *
     * Android hands back an EMPTY LIST rather than an error when location is off
     * or the permission is missing, so "no Wi-Fi found" is almost never the radio.
     * This names the actual blocker.
     */
    private fun selfCheck() {
        val sb = StringBuilder()
        var blocker: String? = null

        val wifiOn = wifi.isWifiEnabled
        sb.appendLine("Wi-Fi radio        ${if (wifiOn) "ON" else "OFF  <-- turn it on"}")
        if (!wifiOn) blocker = "Wi-Fi is switched off on the phone."

        val locPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        sb.appendLine("Location permission ${if (locPerm) "granted" else "DENIED  <-- grant it"}")
        if (locPerm.not() && blocker == null)
            blocker = "Location permission is denied. Settings > Apps > WiFi Survey > Permissions > Location."

        val locOn = locationOn()
        sb.appendLine("Location services   ${if (locOn) "ON" else "OFF  <-- turn it on"}")
        if (!locOn && blocker == null)
            blocker = "Location services are off system-wide. Settings > Location. " +
                      "Android returns an empty scan list when this is off, even with permission granted."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
            sb.appendLine("Nearby devices      ${if (nearby) "granted" else "DENIED  <-- grant it"}")
            if (!nearby && blocker == null)
                blocker = "Nearby devices permission denied (needed on Android 13+)."
        }

        @Suppress("DEPRECATION") val conn = wifi.connectionInfo
        sb.appendLine("Connected to        ${conn?.ssid ?: "nothing"}")
        sb.appendLine("Gateway             ${detectGateway() ?: "not found"}")

        @Suppress("DEPRECATION") val started = wifi.startScan()
        sb.appendLine("startScan()         ${if (started) "accepted" else "refused (throttled)"}")

        val results = try {
            @Suppress("DEPRECATION") wifi.scanResults
        } catch (e: SecurityException) {
            sb.appendLine("getScanResults()    SecurityException: ${e.message}")
            if (blocker == null) blocker = "getScanResults() threw SecurityException."
            emptyList()
        }
        sb.appendLine("APs visible         ${results.size}")

        val withLoad = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            results.count { WifiScanner.bssLoadOf(it) != null } else 0
        sb.appendLine("of those, BSS Load  $withLoad")

        if (results.isEmpty() && blocker == null)
            blocker = "Permissions look fine but the scan returned nothing. " +
                      "Wait two minutes for the scan limit to reset, then try again. " +
                      "If it stays empty, reboot the phone."

        if (withLoad == 0 && results.isNotEmpty())
            sb.appendLine("\nNote: no access point here advertises BSS Load. " +
                          "That is a known possibility. The analysis falls back to the " +
                          "loaded/idle latency ratio instead, which still separates " +
                          "congestion from weak coverage.")

        status.text = blocker ?: "All checks passed. ${results.size} APs visible."
        result.text = sb.toString()
    }

    private fun export() {
        if (store.count() == 0) { status.text = "Nothing to export yet."; return }
        val name = store.exportToDownloads()
        status.text = if (name != null)
            "Exported ${store.count()} measurements to Downloads/$name"
        else "Export failed. Try again, or pull the file over USB."
    }
}
