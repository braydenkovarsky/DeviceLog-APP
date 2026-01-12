package com.example.infocore

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class NetworkActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvConnectionType: TextView
    private lateinit var tvSignal: TextView
    private lateinit var spinnerServers: Spinner
    private lateinit var btnRunPing: Button
    private lateinit var tvPingResult: TextView
    private lateinit var tvLastPingTime: TextView
    private lateinit var progressPing: ProgressBar
    private lateinit var etCustomServer: EditText
    private lateinit var btnPingCustom: Button
    private lateinit var tvPingHistory: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnSaveHistory: Button
    private lateinit var btnClearHistory: Button

    // Logic Variables
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var mobileSignalPercent: Int = -1
    private val pingHistory = mutableListOf<String>()
    private val pingValues = mutableListOf<Long>()
    private var noConnectionDialog: AlertDialog? = null

    // Futuristic Color Palette (Matches Dashboard)
    private val COLOR_CYAN = "#64FFDA"
    private val COLOR_YELLOW = "#FFD54F"
    private val COLOR_RED = "#FF5252"
    private val COLOR_GREY = "#B0BEC5"

    private val dnsServers = listOf(
        "Google DNS (8.8.8.8)" to "8.8.8.8",
        "Cloudflare DNS (1.1.1.1)" to "1.1.1.1",
        "Quad9 DNS (9.9.9.9)" to "9.9.9.9",
        "OpenDNS (208.67.222.222)" to "208.67.222.222"
    )

    private val updateInterval: Long = 2000 // 2 seconds for signal refresh
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateConnectionInfo()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

        // Initialize UI
        tvConnectionType = findViewById(R.id.tvConnectionType)
        tvSignal = findViewById(R.id.tvSignal)
        spinnerServers = findViewById(R.id.spinnerServers)
        btnRunPing = findViewById(R.id.btnRunPing)
        tvPingResult = findViewById(R.id.tvPingResult)
        tvLastPingTime = findViewById(R.id.tvLastPingTime)
        progressPing = findViewById(R.id.progressPing)
        etCustomServer = findViewById(R.id.etCustomServer)
        btnPingCustom = findViewById(R.id.btnPingCustom)
        tvPingHistory = findViewById(R.id.tvPingHistory)
        scrollView = findViewById(R.id.scrollView)
        btnSaveHistory = findViewById(R.id.btnSaveHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        setupMobileSignalListener()
        handler.post(updateRunnable)

        // --- FIXED SPINNER SETUP ---
        // We use R.layout.spinner_item (your custom XML with white text)
        // and simple_spinner_dropdown_item to handle the popup list.
        val serverNames = dnsServers.map { it.first }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, serverNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServers.adapter = adapter

        // Listeners
        btnRunPing.setOnClickListener {
            val serverIp = dnsServers[spinnerServers.selectedItemPosition].second
            runPing(serverIp)
        }

        btnPingCustom.setOnClickListener {
            val serverIp = etCustomServer.text.toString().trim()
            if (serverIp.isNotEmpty()) runPing(serverIp)
            else Toast.makeText(this, "Enter a valid IP or DNS", Toast.LENGTH_SHORT).show()
        }

        btnSaveHistory.setOnClickListener { saveHistoryToFile() }

        btnClearHistory.setOnClickListener {
            pingHistory.clear()
            pingValues.clear()
            tvPingHistory.text = "No history logs yet."
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        executor.shutdown()
    }

    // ==================== CONNECTION LOGIC ====================

    private fun updateConnectionInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        val isConnected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (isConnected) {
            noConnectionDialog?.dismiss()
            noConnectionDialog = null

            val connType = when {
                capabilities!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                else -> "Active"
            }
            val signalText = if (connType == "Wi-Fi") getWifiSignalLabel() else "$mobileSignalPercent%"

            tvConnectionType.text = "Connection: $connType"
            tvSignal.text = "Signal: $signalText"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_CYAN))
        } else {
            tvConnectionType.text = "Connection: Offline"
            tvSignal.text = "Signal: None"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_RED))
            showNoConnectionDialog()
        }
    }

    private fun getWifiSignalLabel(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rssi = wifiManager.connectionInfo.rssi
            when (WifiManager.calculateSignalLevel(rssi, 5)) {
                4 -> "Excellent"
                3 -> "Good"
                2 -> "Fair"
                else -> "Poor"
            }
        } catch (e: Exception) { "Unknown" }
    }

    private fun setupMobileSignalListener() {
        try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            tm.listen(object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    mobileSignalPercent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signalStrength.level * 25
                    } else {
                        (signalStrength.gsmSignalStrength * 100 / 31).coerceIn(0, 100)
                    }
                }
            }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) { mobileSignalPercent = -1 }
    }

    // ==================== PING LOGIC ====================

    private fun runPing(serverIp: String) {
        tvPingResult.text = "Testing $serverIp..."
        progressPing.visibility = ProgressBar.VISIBLE

        executor.execute {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(serverIp).isReachable(2000)
                val ping = if (reachable) System.currentTimeMillis() - start else -1
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                handler.post {
                    progressPing.visibility = ProgressBar.GONE
                    val resultText = if (ping >= 0) "$ping ms" else "Timeout"
                    tvPingResult.text = "Result: $resultText"

                    val colorHex = when {
                        ping in 0..99 -> COLOR_CYAN
                        ping in 100..299 -> COLOR_YELLOW
                        else -> COLOR_RED
                    }
                    tvPingResult.setTextColor(Color.parseColor(colorHex))
                    tvLastPingTime.text = "Last Check: $timestamp"

                    pingHistory.add("$timestamp - $serverIp: $resultText")
                    if (ping >= 0) pingValues.add(ping)
                    updateHistoryDisplay()
                }
            } catch (e: Exception) {
                handler.post {
                    progressPing.visibility = ProgressBar.GONE
                    tvPingResult.text = "Result: Error"
                    tvPingResult.setTextColor(Color.parseColor(COLOR_RED))
                }
            }
        }
    }

    private fun updateHistoryDisplay() {
        val builder = SpannableStringBuilder()
        pingHistory.takeLast(10).forEach { line ->
            val spannable = SpannableString(line + "\n")
            val colorHex = when {
                line.contains("Timeout") -> COLOR_RED
                line.contains("ms") -> {
                    val value = line.substringAfter(": ").substringBefore(" ms").toLongOrNull() ?: 500
                    if (value < 100) COLOR_CYAN else if (value < 300) COLOR_YELLOW else COLOR_RED
                }
                else -> COLOR_GREY
            }
            spannable.setSpan(ForegroundColorSpan(Color.parseColor(colorHex)), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.append(spannable)
        }
        tvPingHistory.text = builder
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ==================== SAVE & EXPORT LOGIC ====================

    private fun saveHistoryToFile() {
        if (pingHistory.isEmpty()) {
            Toast.makeText(this, "No history to save", Toast.LENGTH_SHORT).show()
            return
        }
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "InfoCore_Ping_Log_$date.txt")
        }
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val uri: Uri? = data.data
            uri?.let {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    val manufacturer = Build.MANUFACTURER.uppercase()
                    val model = Build.MODEL
                    val deviceName = "$manufacturer $model (${Build.DEVICE})"
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    val builder = StringBuilder()
                    builder.append("==================================================\n")
                    builder.append("       INFOCORE NETWORK DIAGNOSTIC REPORT         \n")
                    builder.append("==================================================\n")
                    builder.append("Device:      $deviceName\n")
                    builder.append("Saved at:    $timestamp\n")
                    builder.append("--------------------------------------------------\n\n")
                    builder.append("Ping History:\n")
                    pingHistory.forEach { builder.append("• $it\n") }

                    if (pingValues.isNotEmpty()) {
                        val avg = pingValues.average().toInt()
                        builder.append("\nSummary Statistics:\n")
                        builder.append("Average Latency: $avg ms\n")
                        builder.append("Total Tests:     ${pingHistory.size}\n")
                    }

                    builder.append("\n--------------------------------------------------\n")
                    builder.append("Legend & Tips:\n")
                    builder.append("• 0-99 ms   → Excellent (Optimal)\n")
                    builder.append("• 100-299 ms → Fair (Noticeable lag)\n")
                    builder.append("• 300+ ms    → Poor (Congestion)\n")
                    builder.append("\nHow to Improve Ping:\n")
                    builder.append("• Use Wi-Fi instead of mobile data.\n")
                    builder.append("• Move closer to the access point.\n")
                    builder.append("• Avoid network congestion (downloads/streams).\n")
                    builder.append("• Disable active VPNs while testing.\n")
                    builder.append("\n==================================================\n")

                    stream.write(builder.toString().toByteArray())
                    Toast.makeText(this, "Diagnostic report saved", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showNoConnectionDialog() {
        if (noConnectionDialog == null) {
            noConnectionDialog = AlertDialog.Builder(this)
                .setTitle("Offline Mode")
                .setMessage("No internet connection detected. Open network settings?")
                .setPositiveButton("Settings") { _, _ -> startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                .setNegativeButton("Ignore", null)
                .show()
        }
    }
}