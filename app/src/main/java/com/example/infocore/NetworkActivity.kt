package com.example.infocore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var mainDashboardContainer: View
    private lateinit var tvConnectionType: TextView
    private lateinit var tvSignal: TextView
    private lateinit var spinnerServers: Spinner
    private lateinit var btnRunPing: Button
    private lateinit var tvPingResult: TextView
    private lateinit var progressPing: ProgressBar
    private lateinit var etCustomServer: EditText
    private lateinit var btnPingCustom: Button
    private lateinit var tvPingHistory: TextView

    // FIXED: Changed from 'val' to 'lateinit var' to allow assignment in initViews()
    private lateinit var btnSaveReport: Button
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

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { output ->
                    val header = "==========================================\n" +
                            "INFOCORE SYSTEM DIAGNOSTIC REPORT\n" +
                            "Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n" +
                            "==========================================\n\n"
                    val reportBody = pingHistory.joinToString("\n")
                    output.write((header + reportBody).toByteArray())
                    Toast.makeText(this, "Log Saved Successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
        setupMasterAnimations()
        setupListeners()
        startLiveMonitor()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        mainDashboardContainer = findViewById(R.id.mainDashboardContainer)
        tvConnectionType = findViewById(R.id.tvConnectionType)
        tvSignal = findViewById(R.id.tvSignal)
        spinnerServers = findViewById(R.id.spinnerServers)
        btnRunPing = findViewById(R.id.btnRunPing)
        tvPingResult = findViewById(R.id.tvPingResult)
        progressPing = findViewById(R.id.progressPing)
        etCustomServer = findViewById(R.id.etCustomServer)
        btnPingCustom = findViewById(R.id.btnPingCustom)
        tvPingHistory = findViewById(R.id.tvPingHistory)

        // These now assign correctly because they are declared as 'var'
        btnSaveReport = findViewById(R.id.btnSaveHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, dnsServers.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerServers.adapter = adapter
    }

    private fun setupMasterAnimations() {
        navView.post {
            navView.pivotX = 0f
            navView.pivotY = 0f
            navView.translationY = -250f
            navView.alpha = 0f
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val interp = 1f - (1f - slideOffset) * (1f - slideOffset)
                drawerView.translationY = (interp - 1) * 200f
                drawerView.alpha = slideOffset
                drawerView.scaleX = 0.88f + (0.12f * interp)
                drawerView.scaleY = 0.88f + (0.12f * interp)

                val scaleVal = 1f - (interp * 0.05f)
                mainDashboardContainer.scaleX = scaleVal
                mainDashboardContainer.scaleY = scaleVal
                mainDashboardContainer.alpha = 1f - (interp * 0.3f)
            }
        })
    }

    private fun setupListeners() {
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        btnRunPing.setOnClickListener { runPing(dnsServers[spinnerServers.selectedItemPosition].second) }

        btnPingCustom.setOnClickListener {
            val ip = etCustomServer.text.toString().trim()
            if (ip.isNotEmpty()) runPing(ip)
        }

        btnClearHistory.setOnClickListener {
            pingHistory.clear()
            updateHistoryDisplay()
            Toast.makeText(this, "Buffer Cleared", Toast.LENGTH_SHORT).show()
        }

        btnSaveReport.setOnClickListener {
            if (pingHistory.isEmpty()) {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "net_report_${System.currentTimeMillis()}.txt")
            }
            saveFileLauncher.launch(intent)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.itemId == R.id.nav_dashboard) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun runPing(ip: String) {
        tvPingResult.text = "Polling..."
        progressPing.visibility = View.VISIBLE
        executor.execute {
            try {
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(ip).isReachable(3000)
                val time = System.currentTimeMillis() - start
                handler.post {
                    progressPing.visibility = View.GONE
                    val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val resText = if (reachable) "$time ms" else "TIMEOUT"
                    tvPingResult.text = "Result: $resText"
                    pingHistory.add("[$ts] $ip: $resText")
                    updateHistoryDisplay()
                }
            } catch (e: Exception) {
                handler.post {
                    progressPing.visibility = View.GONE
                    tvPingResult.text = "Error"
                }
            }
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
    }

    private fun startLiveMonitor() {
        handler.post(object : Runnable {
            override fun run() {
                updateRealTimeNetwork()
                handler.postDelayed(this, 2500)
            }
        })
    }

    private fun updateRealTimeNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)

        if (caps != null) {
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            tvConnectionType.text = if (isWifi) "Link: Wi-Fi" else "Link: Cellular"

            if (isWifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val rssi = wm.connectionInfo.rssi
                tvSignal.text = "Signal: $rssi dBm"
                tvSignal.setTextColor(Color.parseColor(if (rssi > -65) COLOR_CYAN else COLOR_RED))
            } else {
                tvSignal.text = "Signal: Active"
                tvSignal.setTextColor(Color.parseColor(COLOR_CYAN))
            }
        } else {
            tvConnectionType.text = "Link: Disconnected"
            tvSignal.text = "Signal: N/A"
            tvSignal.setTextColor(Color.parseColor(COLOR_RED))
        }
    }
}