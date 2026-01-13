package com.example.infocore

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.telephony.*
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class NetworkActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tvConnectionType: TextView
    private lateinit var tvSignal: TextView
    private lateinit var spinnerServers: Spinner
    private lateinit var btnRunPing: Button
    private lateinit var tvPingResult: TextView
    private lateinit var progressPing: ProgressBar
    private lateinit var etCustomServer: EditText
    private lateinit var btnPingCustom: Button
    private lateinit var tvPingHistory: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnSaveHistory: Button
    private lateinit var btnClearHistory: Button

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val pingHistory = mutableListOf<String>()

    private val COLOR_BG = "#0F111A"
    private val COLOR_CYAN = "#64FFDA"
    private val COLOR_RED = "#FF5252"

    private val dnsServers = listOf(
        "Google DNS (8.8.8.8)" to "8.8.8.8",
        "Cloudflare DNS (1.1.1.1)" to "1.1.1.1",
        "Quad9 DNS (9.9.9.9)" to "9.9.9.9"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor(COLOR_BG)
            navigationBarColor = Color.parseColor(COLOR_BG)
        }
        setContentView(R.layout.activity_network)

        initViews()
        setupListeners()
        checkPermissions()
        startLiveMonitor()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        tvConnectionType = findViewById(R.id.tvConnectionType)
        tvSignal = findViewById(R.id.tvSignal)
        spinnerServers = findViewById(R.id.spinnerServers)
        btnRunPing = findViewById(R.id.btnRunPing)
        tvPingResult = findViewById(R.id.tvPingResult)
        progressPing = findViewById(R.id.progressPing)
        etCustomServer = findViewById(R.id.etCustomServer)
        btnPingCustom = findViewById(R.id.btnPingCustom)
        tvPingHistory = findViewById(R.id.tvPingHistory)
        scrollView = findViewById(R.id.scrollView)
        btnSaveHistory = findViewById(R.id.btnSaveHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, dnsServers.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerServers.adapter = adapter
    }

    private fun setupListeners() {
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_dashboard) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            drawerLayout.closeDrawers()
            true
        }

        btnRunPing.setOnClickListener { runPing(dnsServers[spinnerServers.selectedItemPosition].second) }
        btnPingCustom.setOnClickListener {
            val ip = etCustomServer.text.toString().trim()
            if (ip.isNotEmpty()) runPing(ip)
        }
        btnSaveHistory.setOnClickListener { saveHistoryToFile() }
        btnClearHistory.setOnClickListener {
            pingHistory.clear()
            tvPingHistory.text = "Logs Wiped."
        }
    }

    private fun startLiveMonitor() {
        handler.post(object : Runnable {
            override fun run() {
                updateRealTimeNetwork()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun updateRealTimeNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        if (caps != null) {
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            tvConnectionType.text = "Link: ${if (isWifi) "Wi-Fi" else "Cellular"}"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_CYAN))

            if (isWifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val level = WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 100)
                tvSignal.text = "Quality: $level%"
            } else {
                tvSignal.text = "Signal: Active"
            }
        } else {
            tvConnectionType.text = "Link: Offline"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_RED))
        }
    }

    private fun runPing(ip: String) {
        tvPingResult.text = "Syncing..."
        progressPing.visibility = View.VISIBLE
        executor.execute {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ip).isReachable(2000)
                val time = System.currentTimeMillis() - start
                handler.post {
                    progressPing.visibility = View.GONE
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val resText = if (reachable) "$time ms" else "TIMEOUT"
                    tvPingResult.text = "Result: $resText"
                    tvPingResult.setTextColor(Color.parseColor(if (reachable) COLOR_CYAN else COLOR_RED))
                    pingHistory.add("[$ts] $ip: $resText")
                    updateHistoryDisplay()
                }
            } catch (e: Exception) { handler.post { progressPing.visibility = View.GONE } }
        }
    }

    private fun updateHistoryDisplay() {
        val builder = SpannableStringBuilder()
        pingHistory.takeLast(10).forEach { line ->
            val span = SpannableString(line + "\n")
            val color = if (line.contains("ms")) COLOR_CYAN else COLOR_RED
            span.setSpan(ForegroundColorSpan(Color.parseColor(color)), 0, span.length, 0)
            builder.append(span)
        }
        tvPingHistory.text = builder
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun generateDiagnosticReport(): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val statFs = StatFs(Environment.getDataDirectory().path)
        val availableStorage = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024 * 1024)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when(pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT WARM"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "THROTTLING"
                else -> "CRITICAL"
            }
        } else "N/A"

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val battTemp = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val battVolt = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
        val isVPN = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wm.connectionInfo
        val freq = if (wifiInfo.frequency > 5000) "5GHz" else "2.4GHz"

        val latencies = pingHistory.mapNotNull { if (it.contains("ms")) it.substringAfter(": ").substringBefore(" ms").toLong() else null }
        val avgLat = if (latencies.isNotEmpty()) latencies.average() else 0.0
        val jitter = if (latencies.size > 1) {
            latencies.zipWithNext { a, b -> Math.abs(a - b) }.average()
        } else 0.0

        return StringBuilder().apply {
            append("╔══════════════════════════════════════════════════╗\n")
            append("║            INFOCORE DIAGNOSTIC REPORT            ║\n")
            append("╚══════════════════════════════════════════════════╝\n\n")

            append("[GENERAL SYSTEM DATA]\n")
            append(String.format("TIMESTAMP      : %s\n", date))
            append(String.format("HARDWARE       : %s %s\n", Build.MANUFACTURER, Build.MODEL))
            append(String.format("THERMAL STATE  : %s\n", thermalStatus))
            append(String.format("STORAGE        : %d GB AVAILABLE\n", availableStorage))
            append(String.format("POWER STATUS   : %.1f°C / %d mV\n", battTemp, battVolt))
            append("----------------------------------------------------\n\n")

            append("[NETWORK CONFIGURATION]\n")
            append(String.format("CONNECTION     : %s\n", tvConnectionType.text))
            append(String.format("VPN STATUS     : %s\n", if (isVPN) "ACTIVE" else "DISABLED"))
            append(String.format("INTERFACE      : %s (%d Mbps)\n", freq, wifiInfo.linkSpeed))
            append(String.format("LOCAL ADDRESS  : %s\n", Formatter.formatIpAddress(wifiInfo.ipAddress)))
            append("----------------------------------------------------\n\n")

            append("[ANALYTICS & TELEMETRY]\n")
            append(String.format("AVERAGE LATENCY: %.2f ms\n", avgLat))
            append(String.format("NETWORK JITTER : %.2f ms\n", jitter))
            append(String.format("PACKET DROP    : %d TIMEOUTS\n", pingHistory.count { it.contains("TIMEOUT") }))
            append("----------------------------------------------------\n\n")

            append(String.format("%-12s | %-20s | %-12s\n", "TIMESTAMP", "TARGET HOST", "LATENCY"))
            append("-------------|----------------------|-------------\n")
            pingHistory.forEach { line ->
                val time = line.substringAfter("[").substringBefore("]")
                val host = line.substringAfter("] ").substringBefore(":")
                val res = line.substringAfter(": ")
                append(String.format("%-12s | %-20s | %-12s\n", time, host, res))
            }
            append("\n[END OF REPORT]\n")
        }.toString()
    }

    private fun saveHistoryToFile() {
        if (pingHistory.isEmpty()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "InfoCore_Report_${System.currentTimeMillis()}.txt")
        }
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { it.write(generateDiagnosticReport().toByteArray()) }
                Toast.makeText(this, "Report successfully saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }
}