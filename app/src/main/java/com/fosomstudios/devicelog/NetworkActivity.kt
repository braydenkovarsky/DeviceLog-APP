package com.fosomstudios.devicelog

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
                            "DeviceLog SYSTEM DIAGNOSTIC REPORT\n" +
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
        btnSaveReport = findViewById(R.id.btnSaveHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, dnsServers.map { it.first })
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerServers.adapter = adapter
    }

    /**
     * Side-Bar Sidebar Mechanism matching MainActivity
     */
    private fun setupMasterAnimations() {
        navView.post {
            navView.pivotX = 0f
            navView.pivotY = 0f
            navView.translationY = -200f
            navView.alpha = 0f
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Bounce-Out logic from MainActivity
                val dropFactor = 1f - (1f - slideOffset) * (1f - slideOffset)

                drawerView.translationY = (dropFactor - 1) * 200f
                drawerView.alpha = slideOffset
                drawerView.scaleX = 0.85f + (0.15f * dropFactor)
                drawerView.scaleY = 0.85f + (0.15f * dropFactor)

                // Dashboard Reaction
                mainDashboardContainer.alpha = 1f - (slideOffset * 0.4f)
                mainDashboardContainer.translationX = slideOffset * 30f
            }

            override fun onDrawerClosed(drawerView: View) {
                mainDashboardContainer.translationX = 0f
                mainDashboardContainer.alpha = 1f
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
    DEVICELOG TERMINAL CORE v1.0.4 
    Revision: 2026.01.22 | Build Architecture: API 35 (Android 16)
    
    1. ANDROID MANIFEST: PERMISSION & COMPONENT LOGIC
    • INTERNET: Authorized for high-frequency handshakes with AdMob bidding clusters and fetching hardware-ID JSON data.
    • ACCESS_NETWORK_STATE & ACCESS_WIFI_STATE: Monitors diagnostic path stability to ensure telemetry data packets are not dropped during hardware stress tests.
    • FOREGROUND_SERVICE & FOREGROUND_SERVICE_SPECIAL_USE: As per API 35 standards, this allows the "TelemetryService" to bypass standard OS throttling, ensuring thermal and RAM monitoring remains active to prevent hardware damage.
    • WAKE_LOCK: This is the "Zero-Gap" protocol. It prevents the CPU from entering deep sleep specifically when the device hits 100% capacity, ensuring "Trickle Charge" phase logging is 100% accurate.
    • POST_NOTIFICATIONS: Required for transparency, allowing the system to maintain a persistent hardware-status overlay in the tray.

    2. BUILD GRADLE: ENGINE ARCHITECTURE
    • Kotlin 2.1.0: Upgraded compiler to handle the metadata requirements of the Google Ads SDK 24.9.0.
    • SDK 35: Compiled for Android 16 (2026) for maximum compatibility with modern hardware.
    • R8/Proguard: Utilizes code shrinking to protect telemetry algorithms and reduce the engine footprint.

    3. FLATICON FREE LICENSE (MANDATORY ATTRIBUTION)
    This software is attributed to its authors as per the license requirements:
    • License Type: Free License (With Attribution).
    • Interface Icons: "Designed by Pixel perfect from Flaticon".
    • Switch/Button Assets: "Designed by Freepik from Flaticon".
    • Usage Scope: Software, applications, mobile, and multimedia.
    • Rights: We hold a non-exclusive, non-transferable right to use these materials worldwide.
    • Legal: The original 'license.pdf' is embedded in the application assets folder for regulatory verification.

    4. DATA INTEGRITY (ZERO-PERSISTENCE RULE)
    All metrics (RAM, Thermal, Amperage) are processed in volatile memory (RAM). No logs are ever written to internal storage or transmitted to external servers.
""".trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Advanced System & Legal Manifest")
            .setMessage(aboutMessage)
            .setPositiveButton("Dismiss", null)
            .setNeutralButton("View License PDF") { _, _ -> openLicensePdf() }
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = false
        }
        webView.loadDataWithBaseURL(null, getProfessionalPrivacyHtml(), "text/html", "UTF-8", null)
        AlertDialog.Builder(this).setTitle("Privacy Protocol").setView(webView).setPositiveButton("Acknowledge", null).show()
    }

    private fun getProfessionalPrivacyHtml(): String {
        return """
<html>
<head>
<style>
    body { background-color:#06080A; color:#8E9AAF; padding:25px; font-family: sans-serif; line-height:1.8; font-size: 13px; }
    h1 { color:#F8FAFC; font-size: 18px; border-bottom: 2px solid #1E293B; padding-bottom:8px; margin-bottom: 20px; }
    h2 { color:#64FFDA; font-size: 12px; margin-top: 25px; text-transform: uppercase; border-left: 3px solid #64FFDA; padding-left: 10px; }
    p { margin-bottom: 12px; }
    .ad-box { border: 1px dashed #64FFDA; padding: 15px; background: rgba(100, 255, 218, 0.05); margin: 20px 0; border-radius: 4px; }
    .footer { font-family:monospace; margin-top:30px; color:#475569; font-size: 10px; text-align: center; }
</style>
</head>
<body>
    <h1>Privacy & Security Protocol</h1>
    
    <h2>1.0 Licensing & Asset Attribution</h2>
    <p>This application complies with the <b>Flaticon Free License</b>. We acknowledge that the graphical assets used for monitoring switches and system icons were designed by <b>Pixel perfect</b> and <b>Freepik</b>. We exercise our non-exclusive, world-wide right to use these licensed materials an unlimited number of times within this mobile software.</p>
    
    <div class="ad-box">
        <h2>2.0 Advertising Disclosure (AdMob)</h2>
        <p>Google AdMob (v24.9.0) is integrated for monetization. Google may collect the Advertising ID (AAID) to serve relevant ads. Telemetry metrics are strictly isolated and are never shared with the ad engine.</p>
    </div>

    <h2>3.0 Manifest & Permission Justification</h2>
    <p>The app requests <b>FOREGROUND_SERVICE_SPECIAL_USE</b> for real-time hardware monitoring. <b>WAKE_LOCK</b> is utilized specifically during the "Trickle Charge" phase (100% capacity) to prevent data gaps during charging stability analysis.</p>

    <h2>4.0 Terms of Usage</h2>
    <p>Per the license, we do not sublicense, sell, or rent these visual assets. We may alter and create derivative works to fit the application's aesthetic. The full license certificate is embedded within the application's assets folder (license.pdf).</p>

    <div class="footer">
        DOCUMENT: DL-2026-REV-D | COMPLIANCE: API_35 / KOTLIN_2.1.0<br>
        VERIFIED: VOLATILE_MEMORY_ONLY
    </div>
</body>
</html>
""".trimIndent()
    }

    private fun openLicensePdf() {
        try {
            val assetManager = assets
            val inputStream = assetManager.open("license.pdf")
            val file = java.io.File(cacheDir, "license.pdf")

            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.provider", file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(contentUri, "application/pdf")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open license: ${e.message}", Toast.LENGTH_LONG).show()
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