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

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var mobileSignalPercent: Int = -1

    private val dnsServers = listOf(
        "Google 8.8.8.8" to "8.8.8.8",
        "Google 8.8.4.4" to "8.8.4.4",
        "Cloudflare 1.1.1.1" to "1.1.1.1",
        "Cloudflare 1.0.0.1" to "1.0.0.1",
        "Quad9 9.9.9.9" to "9.9.9.9",
        "OpenDNS 208.67.222.222" to "208.67.222.222"
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

        setupMobileSignalListener()
        handler.post(updateRunnable)

        val serverNames = dnsServers.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerServers.adapter = adapter

        btnRunPing.setOnClickListener {
            val selectedIndex = spinnerServers.selectedItemPosition
            val serverIp = dnsServers[selectedIndex].second
            runPing(serverIp)
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

        val connType = when {
            capabilities == null -> "No Connection"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            else -> "Unknown"
        }
        tvConnectionType.text = "Connection: $connType"

        val signalText = when (connType) {
            "Wi-Fi" -> getWifiSignal()
            "Mobile Data" -> if (mobileSignalPercent >= 0) "$mobileSignalPercent%" else "Calculating..."
            else -> "0%"
        }
        tvSignal.text = "Signal: $signalText"
    }

    private fun getWifiSignal(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            if (info.networkId == -1) "No Wi-Fi"
            else {
                val level = WifiManager.calculateSignalLevel(info.rssi, 100)
                "$level%"
            }
        } catch (e: Exception) {
            "0%"
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
