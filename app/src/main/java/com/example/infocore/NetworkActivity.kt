package com.example.infocore

import android.Manifest
import android.content.Context
import android.content.Intent
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

        // --- STATUS BAR BLACKOUT FIX ---
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor(COLOR_BG)
            navigationBarColor = Color.parseColor(COLOR_BG)
        }

        setContentView(R.layout.activity_network)

        // Initialize All Views
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

        // Setup Spinner
        val adapter = ArrayAdapter(this, R.layout.spinner_item, dnsServers.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerServers.adapter = adapter

        // --- NAVIGATION DRAWER LOGIC ---
        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.nav_network -> drawerLayout.closeDrawers()
                // Add other menu IDs here to make them work
            }
            drawerLayout.closeDrawers()
            true
        }

        // --- PING & UTILITY LOGIC ---
        btnRunPing.setOnClickListener { runPing(dnsServers[spinnerServers.selectedItemPosition].second) }

        btnPingCustom.setOnClickListener {
            val ip = etCustomServer.text.toString().trim()
            if (ip.isNotEmpty()) runPing(ip)
        }

        btnSaveHistory.setOnClickListener { saveHistoryToFile() }

        btnClearHistory.setOnClickListener {
            pingHistory.clear()
            tvPingHistory.text = "No history logs yet."
            Toast.makeText(this, "History Cleared", Toast.LENGTH_SHORT).show()
        }

        checkPermissions()
        startLiveMonitor()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
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
            tvConnectionType.text = "Connection: ${if (isWifi) "Wi-Fi" else "Mobile Data"}"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_CYAN))

            if (isWifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val rssi = wm.connectionInfo.rssi
                val level = WifiManager.calculateSignalLevel(rssi, 100)
                tvSignal.text = "Signal: ${categorizeSignal(level)}"
            } else {
                tvSignal.text = "Signal: ${getMobileSignalCategory()}"
            }
        } else {
            tvConnectionType.text = "Connection: Offline"
            tvConnectionType.setTextColor(Color.parseColor(COLOR_RED))
            tvSignal.text = "Signal: N/A"
        }
    }

    private fun categorizeSignal(level: Int): String {
        return when {
            level > 75 -> "Excellent"
            level > 40 -> "Good"
            level > 0 -> "Poor"
            else -> "N/A"
        }
    }

    private fun getMobileSignalCategory(): String {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val allInfo = tm.allCellInfo
            val info = allInfo?.firstOrNull { it.isRegistered }
            val level = when (info) {
                is CellInfoLte -> info.cellSignalStrength.level
                is CellInfoGsm -> info.cellSignalStrength.level
                is CellInfoWcdma -> info.cellSignalStrength.level
                else -> 0
            }
            return when (level) {
                4 -> "Excellent"
                3 -> "Good"
                1, 2 -> "Poor"
                else -> "N/A"
            }
        }
        return "N/A"
    }

    private fun runPing(ip: String) {
        tvPingResult.text = "Pinging..."
        progressPing.visibility = View.VISIBLE
        executor.execute {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ip).isReachable(2000)
                val time = System.currentTimeMillis() - start
                handler.post {
                    progressPing.visibility = View.GONE
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    if (reachable) {
                        tvPingResult.text = "Result: $time ms"
                        tvPingResult.setTextColor(Color.parseColor(COLOR_CYAN))
                        pingHistory.add("[$timestamp] $ip: $time ms")
                    } else {
                        tvPingResult.text = "Result: Timeout"
                        tvPingResult.setTextColor(Color.parseColor(COLOR_RED))
                        pingHistory.add("[$timestamp] $ip: Timeout")
                    }
                    updateHistoryDisplay()
                }
            } catch (e: Exception) {
                handler.post { progressPing.visibility = View.GONE }
            }
        }
    }

    private fun updateHistoryDisplay() {
        val builder = SpannableStringBuilder()
        pingHistory.takeLast(10).forEach { line ->
            val span = SpannableString(line + "\n")
            val color = if (line.contains("ms")) COLOR_CYAN else COLOR_RED
            span.setSpan(ForegroundColorSpan(Color.parseColor(color)), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.append(span)
        }
        tvPingHistory.text = builder
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun saveHistoryToFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "NetworkLog_${System.currentTimeMillis()}.txt")
        }
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use {
                    it.write(pingHistory.joinToString("\n").toByteArray())
                }
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
}