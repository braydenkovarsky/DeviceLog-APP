package com.example.infocore

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

// NEW: For ping history coloring
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import android.net.Uri

class NetworkActivity : AppCompatActivity() {

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

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var mobileSignalPercent: Int = -1
    private val pingHistory = mutableListOf<String>()

    private var isNoConnectionDialogShown = false
    private var noConnectionPopupCount = 0

    private val dnsServers = listOf(
        "Google 8.8.8.8" to "8.8.8.8",
        "Google 8.8.4.4" to "8.8.4.4",
        "Cloudflare 1.1.1.1" to "1.1.1.1",
        "Cloudflare 1.0.0.1" to "1.0.0.1",
        "Quad9 9.9.9.9" to "9.9.9.9"
    )

    private val updateInterval: Long = 3000
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateConnectionInfo()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

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

        val serverNames = dnsServers.map { it.first }
        spinnerServers.adapter = ArrayAdapter(this, R.layout.spinner_item, serverNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }

        btnRunPing.setOnClickListener {
            val selectedIndex = spinnerServers.selectedItemPosition
            val serverIp = dnsServers[selectedIndex].second
            runPing(serverIp)
        }

        btnPingCustom.setOnClickListener {
            val serverIp = etCustomServer.text.toString().trim()
            if (serverIp.isNotEmpty()) runPing(serverIp)
            else Toast.makeText(this, "Enter a valid IP or DNS", Toast.LENGTH_SHORT).show()
        }

        // Save history
        btnSaveHistory.setOnClickListener {
            saveHistoryToFile()
        }

        // Clear history
        btnClearHistory.setOnClickListener {
            pingHistory.clear()
            tvPingHistory.text = "No history yet"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        executor.shutdown()
    }

    private fun updateConnectionInfo() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)

        val connType: String
        val signalText: String

        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    connType = "Wi-Fi"
                    signalText = getWifiSignalLabel()
                    isNoConnectionDialogShown = false
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    connType = "Mobile Data"
                    signalText = if (mobileSignalPercent >= 0) "$mobileSignalPercent%" else "Calculating..."
                    isNoConnectionDialogShown = false
                }
                else -> {
                    connType = "No Connection"
                    signalText = "None"
                    showNoConnectionDialog()
                }
            }
        } else {
            connType = "No Connection"
            signalText = "None"
            showNoConnectionDialog()
        }

        tvConnectionType.text = "Connection: $connType"
        tvSignal.text = "Signal: $signalText"
    }

    private fun showNoConnectionDialog() {
        if (!isNoConnectionDialogShown) {
            isNoConnectionDialogShown = true
            AlertDialog.Builder(this)
                .setTitle("No Connection Detected")
                .setMessage("Your device is not connected to the internet. Would you like to open Wi-Fi settings?")
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog, _ ->
                    noConnectionPopupCount++
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    dialog.dismiss()
                    if (noConnectionPopupCount >= 3) showWifiFixRecommendation()
                }
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun showWifiFixRecommendation() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)

        val message = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                "You are connected to Wi-Fi but still facing issues. Try:\n\n" +
                        "• Restart your router.\n" +
                        "• Move closer to the Wi-Fi access point.\n" +
                        "• Forget and reconnect to your Wi-Fi network.\n" +
                        "• Check if other devices can connect to the same network.\n" +
                        "• Update your device software."
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                "You are on Mobile Data but experiencing connectivity issues. Try:\n\n" +
                        "• Toggle Airplane mode on/off.\n" +
                        "• Ensure you have good signal coverage.\n" +
                        "• Disable data saver or VPN apps.\n" +
                        "• Restart your device.\n" +
                        "• Contact your mobile provider if the problem persists."
            }
            else -> {
                "Your device is currently offline. Try:\n\n" +
                        "• Enable Wi-Fi or Mobile Data.\n" +
                        "• Restart your router or device.\n" +
                        "• Check for software updates.\n" +
                        "• Reset network settings if needed."
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Connection Recommendations")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getWifiSignalLabel(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val rssi = info.rssi
            if (rssi <= -127) return "None"
            when (WifiManager.calculateSignalLevel(rssi, 5)) {
                0 -> "Very Poor"
                1 -> "Poor"
                2 -> "Fair"
                3 -> "Good"
                4 -> "Excellent"
                else -> "Unknown"
            }
        } catch (e: Exception) { "None" }
    }

    private fun setupMobileSignalListener() {
        try {
            val tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    super.onSignalStrengthsChanged(signalStrength)
                    mobileSignalPercent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        signalStrength.level * 25
                    } else {
                        val gsmSignal = signalStrength.gsmSignalStrength
                        (gsmSignal * 100 / 31).coerceIn(0, 100)
                    }
                }
            }
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) { mobileSignalPercent = -1 }
    }

    private fun runPing(serverIp: String) {
        tvPingResult.text = "Pinging $serverIp..."
        progressPing.visibility = ProgressBar.VISIBLE

        executor.execute {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(serverIp).isReachable(2000)
                val ping = if (reachable) System.currentTimeMillis() - start else -1
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                handler.post {
                    progressPing.visibility = ProgressBar.GONE
                    val pingText = if (ping >= 0) "$serverIp → $ping ms" else "$serverIp → Timeout"
                    tvPingResult.text = pingText
                    tvPingResult.setTextColor(
                        when {
                            ping in 0..99 -> getColor(R.color.green_ping)
                            ping in 100..299 -> getColor(R.color.yellow_ping)
                            else -> getColor(R.color.red_ping)
                        }
                    )
                    tvLastPingTime.text = "Last ping: $timestamp"

                    val historyLine = "$timestamp - $pingText"
                    pingHistory.add(historyLine)

                    val historyBuilder = SpannableStringBuilder()
                    pingHistory.forEach { line ->
                        val spannableLine = SpannableString(line)
                        val pingMatch = "(\\d+ ms|Timeout)".toRegex().find(line)
                        if (pingMatch != null) {
                            val pingValue = pingMatch.value
                            val color = when {
                                pingValue.contains("Timeout") -> getColor(R.color.red_ping)
                                pingValue.replace(" ms", "").toInt() in 0..99 -> getColor(R.color.green_ping)
                                pingValue.replace(" ms", "").toInt() in 100..299 -> getColor(R.color.yellow_ping)
                                else -> getColor(R.color.red_ping)
                            }
                            spannableLine.setSpan(
                                ForegroundColorSpan(color),
                                pingMatch.range.first,
                                pingMatch.range.last + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        historyBuilder.append(spannableLine)
                        historyBuilder.append("\n")
                    }

                    tvPingHistory.text = historyBuilder
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            } catch (e: Exception) {
                handler.post {
                    progressPing.visibility = ProgressBar.GONE
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val historyLine = "$timestamp - $serverIp → Timeout"
                    pingHistory.add(historyLine)

                    val spannableLine = SpannableString(historyLine)
                    spannableLine.setSpan(
                        ForegroundColorSpan(getColor(R.color.red_ping)),
                        historyLine.indexOf("Timeout"),
                        historyLine.indexOf("Timeout") + "Timeout".length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    val historyBuilder = SpannableStringBuilder()
                    pingHistory.forEach { line ->
                        val lineSpan = SpannableString(line)
                        historyBuilder.append(lineSpan)
                        historyBuilder.append("\n")
                    }

                    tvPingHistory.text = historyBuilder
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

                    tvPingResult.text = "$serverIp → Error"
                    tvPingResult.setTextColor(getColor(R.color.red_ping))
                    tvLastPingTime.text = "Last ping: $timestamp"
                }
            }
        }
    }

    private fun saveHistoryToFile() {
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TITLE, "ping_history_${System.currentTimeMillis()}.txt")
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val uri: Uri? = data.data
            uri?.let {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    // Build beautifully formatted file
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val sb = StringBuilder()
                    sb.appendLine("========================================")
                    sb.appendLine("           NETWORK PING HISTORY")
                    sb.appendLine("========================================")
                    sb.appendLine("Device: InfoCore Ping Tester")
                    sb.appendLine("Date: $dateStr")
                    sb.appendLine("Saved at: $timeStr")
                    sb.appendLine("----------------------------------------")
                    pingHistory.forEach { line -> sb.appendLine(line) }
                    sb.appendLine("----------------------------------------")
                    sb.appendLine("Legend:")
                    sb.appendLine("Green = Good (0-99ms)")
                    sb.appendLine("Yellow = Fair (100-299ms)")
                    sb.appendLine("Red = Timeout or Poor (>299ms)")
                    sb.appendLine("========================================")
                    stream.write(sb.toString().toByteArray())
                    Toast.makeText(this, "History saved", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
