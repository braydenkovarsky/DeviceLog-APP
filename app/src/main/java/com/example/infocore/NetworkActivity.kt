package com.example.infocore

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

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

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var mobileSignalPercent: Int = -1

    private val dnsServers = listOf(
        "Google 8.8.8.8" to "8.8.8.8",
        "Google 8.8.4.4" to "8.8.4.4",
        "Cloudflare 1.1.1.1" to "1.1.1.1",
        "Cloudflare 1.0.0.1" to "1.0.0.1",
        "Quad9 9.9.9.9" to "9.9.9.9",
        "Quad9 149.112.112.112" to "149.112.112.112",
        "OpenDNS 208.67.222.222" to "208.67.222.222",
        "OpenDNS 208.67.220.220" to "208.67.220.220",
        "OpenDNS FamilyShield 208.67.222.123" to "208.67.222.123",
        "OpenDNS FamilyShield 208.67.220.123" to "208.67.220.123",
        "CleanBrowsing Family 185.228.168.168" to "185.228.168.168",
        "CleanBrowsing Adult 185.228.168.10" to "185.228.168.10",
        "CleanBrowsing Security 185.228.168.9" to "185.228.168.9",
        "DNS.Watch 84.200.69.80" to "84.200.69.80",
        "DNS.Watch 84.200.70.40" to "84.200.70.40",
        "Verisign Public DNS 64.6.64.6" to "64.6.64.6",
        "Verisign Public DNS 64.6.65.6" to "64.6.65.6",
        "AdGuard DNS 94.140.14.14" to "94.140.14.14",
        "AdGuard DNS 94.140.15.15" to "94.140.15.15",
        "SafeDNS 195.46.39.39" to "195.46.39.39",
        "SafeDNS 195.46.39.40" to "195.46.39.40",
        "Yandex DNS 77.88.8.8" to "77.88.8.8",
        "Yandex DNS 77.88.8.1" to "77.88.8.1",
        "Alternate DNS 198.101.242.72" to "198.101.242.72",
        "Alternate DNS 23.253.163.53" to "23.253.163.53",
        "SmartViper 208.76.50.50" to "208.76.50.50",
        "SmartViper 208.76.51.51" to "208.76.51.51",
        "Level3 209.244.0.3" to "209.244.0.3",
        "Level3 209.244.0.4" to "209.244.0.4",
        "Comodo Secure DNS 8.26.56.26" to "8.26.56.26",
        "Comodo Secure DNS 8.20.247.20" to "8.20.247.20",
        "Freenom World 80.80.80.80" to "80.80.80.80",
        "Freenom World 80.80.81.81" to "80.80.81.81"
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

        setupMobileSignalListener()
        handler.post(updateRunnable)

        val serverNames = dnsServers.map { it.first }

        // Spinner adapter with black background and white text, works on all themes
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
            if (serverIp.isNotEmpty()) {
                runPing(serverIp)
            } else {
                Toast.makeText(this, "Enter a valid IP or DNS", Toast.LENGTH_SHORT).show()
            }
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
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    connType = "Mobile Data"
                    signalText = if (mobileSignalPercent >= 0) "$mobileSignalPercent%" else "Calculating..."
                }
                else -> {
                    connType = "No Connection"
                    signalText = "None"
                }
            }
        } else {
            connType = "No Connection"
            signalText = "None"
        }

        tvConnectionType.text = "Connection: $connType"
        tvSignal.text = "Signal: $signalText"
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
        } catch (e: Exception) {
            "None"
        }
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
        } catch (e: Exception) {
            mobileSignalPercent = -1
        }
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
                }
            } catch (e: Exception) {
                handler.post {
                    progressPing.visibility = ProgressBar.GONE
                    tvPingResult.text = "$serverIp → Error"
                    tvPingResult.setTextColor(getColor(R.color.red_ping))
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    tvLastPingTime.text = "Last ping: $timestamp"
                }
            }
        }
    }
}
