package com.example.infocore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var tvConnectionType: TextView
    private lateinit var tvSignal: TextView
    private lateinit var spinnerServers: Spinner
    private lateinit var btnRunPing: Button
    private lateinit var tvPingResult: TextView
    private lateinit var progressPing: ProgressBar
    private lateinit var etCustomServer: EditText
    private lateinit var btnPingCustom: Button
    private lateinit var tvPingHistory: TextView
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
                            "Revision: 2026.SEC-X1\n" +
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
        setupListeners()
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
        btnSaveReport = findViewById(R.id.btnSaveHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, dnsServers.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerServers.adapter = adapter
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
            Toast.makeText(this, "Telemetry Buffer Purged", Toast.LENGTH_SHORT).show()
        }

        btnSaveReport.setOnClickListener {
            if (pingHistory.isEmpty()) {
                Toast.makeText(this, "No telemetry data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "network_diagnostic_${System.currentTimeMillis()}.txt")
            }
            saveFileLauncher.launch(intent)
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                R.id.menuInfo -> showAboutDialog()
                R.id.menuPrivacy -> showPrivacyPolicyDialog()
            }
            drawerLayout.closeDrawers()
            true
        }
    }

    private fun showAboutDialog() {
        val aboutMessage = """
            SYSTEM INTELLIGENCE INTERFACE
            Build Version: 1.1.2 [STABLE]
            
            ARCHITECTURE OVERVIEW:
            This application is engineered by an individual developer as a high-fidelity diagnostic utility. By interfacing with low-level hardware abstraction layers, the system synthesizes real-time telemetry regarding electrical, thermal, and computational states.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("System Documentation")
            .setMessage(aboutMessage)
            .setPositiveButton("Dismiss", null)
            .setNeutralButton("Submit Bug") { _, _ ->
                startActivity(Intent(this, BugReportActivity::class.java))
            }
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        val webView = WebView(this)
        val htmlContent = getProfessionalPrivacyHtml()
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        AlertDialog.Builder(this)
            .setTitle("Privacy & Security Protocol")
            .setView(webView)
            .setPositiveButton("Acknowledge", null)
            .show()
    }

    private fun getProfessionalPrivacyHtml(): String {
        return """
            <html>
            <head>
            <style>
                body { background-color:#0A0C14; color:#94A3B8; padding:30px; font-family: sans-serif; line-height:1.8; font-size: 11px; }
                .header { border-bottom: 2px solid #1E293B; padding-bottom: 20px; margin-bottom: 30px; }
                h1 { color:#64FFDA; font-size: 18px; letter-spacing: 1px; margin:0; text-transform: uppercase; }
                h2 { color:#E2E8F0; font-size: 13px; margin-top: 30px; border-left: 3px solid #64FFDA; padding-left: 15px; text-transform: uppercase; }
                p { margin-bottom: 15px; text-align: justify; }
                b { color:#F1F5F9; font-weight: 600; }
                .footer { margin-top: 50px; padding-top: 20px; border-top: 1px solid #1E293B; font-size: 10px; color: #475569; text-align: center; }
            </style>
            </head>
            <body>
                <div class="header">
                    <h1>Data Integrity & Privacy Protocol</h1>
                    <p>Architecture Revision: 2026.SEC-X1 // Individual Dev Build</p>
                </div>
                <h2>1.0 DESIGN INTENTION</h2>
                <p>InfoCore is built with the fundamental intention of providing <b>absolute data authenticity and security</b>. Every design choice is dictated by the requirement for secure, localized hardware transparency. The system architecture fundamentally rejects the integration of cloud-based telemetry, ensuring that diagnostic variables remain confined to the origin device.</p>
                <h2>2.0 HARDWARE & STORAGE ENCAPSULATION</h2>
                <p>Telemetry involving PMIC (Power Management Integrated Circuit) polling, voltage analysis, and thermal state synthesis is processed via <b>Isolated Subsystem Queries</b>. Storage analysis permissions (including MANAGE_EXTERNAL_STORAGE) are utilized exclusively for <b>Volume Capacity Analysis</b> and block-level measurement. The system does not index, read, or cache personal user files, photos, or documents.</p>
                <h2>3.0 VOLATILE MEMORY ARCHITECTURE</h2>
                <p>To ensure maximum security and forensic resistance, the system utilizes a <b>Purely Volatile Framework</b>. 
                <ul>
                    <li><b>Transient State:</b> Data is processed in real-time RAM and is never committed to persistent storage.</li>
                    <li><b>Cycle Purge:</b> Hardware polling cycles are ephemeral; previous telemetry states are discarded as new data is synthesized.</li>
                    <li><b>Process Isolation:</b> Termination triggers an immediate purge of the memory heap allocated for hardware monitoring.</li>
                </ul></p>
                <h2>4.0 CORE INTEGRITY STANDARDS</h2>
                <p>This software is built using only native system libraries to ensure the code remains clean and auditable. All third-party SDKs, analytics beacons, and tracking identifiers have been intentionally omitted from the source code.</p>
                <div class="footer">
                    ENGINEERED FOR AUTHENTICITY AND SYSTEM SECURITY<br>
                    SECURE ARCHITECTURE BY DESIGN.
                </div>
            </body>
            </html>
        """.trimIndent()
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
            if (isWifi) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val rssi = wm.connectionInfo.rssi
                tvSignal.text = "Signal: ${WifiManager.calculateSignalLevel(rssi, 100)}%"
            }
        }
    }

    private fun runPing(ip: String) {
        tvPingResult.text = "Polling..."
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
                    pingHistory.add("[$ts] $ip: $resText")
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
            span.setSpan(ForegroundColorSpan(Color.parseColor(color)), 0, span.length, 0)
            builder.append(span)
        }
        tvPingHistory.text = builder
    }
}