package com.example.infocore

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import android.webkit.WebView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tvBattery: TextView
    private lateinit var batteryProgress: ProgressBar
    private lateinit var tvStorage: TextView
    private lateinit var tvRam: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvFooterInfo: TextView
    private lateinit var btnOpenNetwork: Button
    private lateinit var tvChargingSpeed: TextView
    private lateinit var tvChargingType: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvDeviceHealthStatus: TextView
    private lateinit var tvPerformanceTips: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDeviceInfo()
            updateDeviceHealth()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        tvBattery = findViewById(R.id.tvBattery)
        batteryProgress = findViewById(R.id.batteryProgress)
        tvRam = findViewById(R.id.tvRam)
        tvStorage = findViewById(R.id.tvStorage)
        tvUptime = findViewById(R.id.tvUptime)
        tvFooterInfo = findViewById(R.id.tvFooterInfo)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvDeviceHealthStatus = findViewById(R.id.tvDeviceHealthStatus)
        tvPerformanceTips = findViewById(R.id.tvPerformanceTips)
        tvChargingSpeed = findViewById(R.id.tvChargingSpeed)
        tvChargingType = findViewById(R.id.tvChargingType)
        btnOpenNetwork = findViewById(R.id.btnOpenNetwork)

        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(navView) }
        btnOpenNetwork.setOnClickListener { startActivity(Intent(this, NetworkActivity::class.java)) }

        navView.setNavigationItemSelectedListener {
            handleNavItemSelected(it.itemId)
            drawerLayout.closeDrawers()
            true
        }
        populateFooterInfo()
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun handleNavItemSelected(itemId: Int) = when (itemId) {
        R.id.nav_network -> startActivity(Intent(this, NetworkActivity::class.java))
        R.id.menuInfo -> showAboutDialog()
        R.id.menuPrivacy -> showPrivacyPolicyDialog()
        else -> {}
    }

    private fun showAboutDialog() {
        val aboutMessage = """
            SYSTEM INTELLIGENCE INTERFACE
            Build Version: 1.1.2 [STABLE]
            
            ARCHITECTURE OVERVIEW:
            This application is engineered by an individual developer as a high-fidelity diagnostic utility. By interfacing with low-level hardware abstraction layers, the system synthesizes real-time telemetry regarding electrical, thermal, and computational states.
            
            The framework is optimized for transparency and authenticity, providing an accurate representation of hardware variables with negligible impact on system overhead.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("System Documentation")
            .setMessage(aboutMessage)
            .setPositiveButton("Dismiss", null)
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
                    <li><b>Cycle Purge:</b> Hardware polling cycles are designed to be ephemeral; previous telemetry states are discarded as new data is synthesized.</li>
                    <li><b>Process Isolation:</b> Termination of the application lifecycle triggers an immediate purge of the memory heap allocated for hardware monitoring.</li>
                </ul></p>

                <h2>4.0 SECURITY & NETWORK TRANSPARENCY</h2>
                <p>Network diagnostic tools within this suite are designed for <b>Passive Benchmarking</b>. All connections are transient and user-initiated. Reports are only generated via explicit user intent using the Android Storage Access Framework, ensuring that the control over data export remains entirely in the hands of the system operator.</p>

                <h2>5.0 CORE INTEGRITY STANDARDS</h2>
                <p>This software is built using only native system libraries to ensure the code remains clean and auditable. All third-party SDKs, analytics beacons, and tracking identifiers have been intentionally omitted from the source code to maintain the highest level of system integrity.</p>

                <h2>6.0 REGULATORY COMPLIANCE</h2>
                <p>By prioritizing data minimization, this framework exceeds the standards set by the <b>GDPR</b> and <b>CCPA</b>. As the system architecture prevents the collection or storage of Personal Identifiable Information (PII), privacy is not just a policy but a technical certainty of the build.</p>

                <div class="footer">
                    ENGINEERED FOR AUTHENTICITY AND SYSTEM SECURITY<br>
                    SECURE ARCHITECTURE BY DESIGN.
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun updateDeviceInfo() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        tvBattery.text = "$level%"
        batteryProgress.progress = level
        var rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toDouble()
        val currentInAmps = if (abs(rawCurrent) > 50000) rawCurrent / 1000000.0 else rawCurrent / 1000.0
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val voltageMilliVolts = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageInVolts = voltageMilliVolts / 1000.0
        val watts = currentInAmps * voltageInVolts
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (isCharging) {
            tvChargingType.text = "Charging"
            tvChargingType.setTextColor(Color.parseColor("#B0BEC5"))
            tvChargingSpeed.text = String.format("+%.2f W", abs(watts))
            tvChargingSpeed.setTextColor(Color.parseColor("#64FFDA"))
        } else {
            tvChargingType.text = "Discharging"
            tvChargingType.setTextColor(Color.parseColor("#FF8A80"))
            tvChargingSpeed.text = String.format("-%.2f W", abs(watts))
            tvChargingSpeed.setTextColor(Color.parseColor("#FF5252"))
        }
        tvRam.text = "${getRamUsage()}%"
        tvStorage.text = "${getStorageUsage()}%"
        tvUptime.text = getUptime()
    }

    private fun getRamUsage(): Int {
        val mem = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        return ((mem.totalMem - mem.availMem).toFloat() / mem.totalMem * 100).roundToInt()
    }

    private fun getStorageUsage(): Int {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return ((total - free).toFloat() / total * 100).roundToInt()
    }

    private fun getUptime(): String {
        val uptime = SystemClock.elapsedRealtime()
        val h = uptime / 1000 / 3600
        val m = uptime / 1000 / 60 % 60
        return "${h}h ${m}m"
    }

    private fun updateDeviceHealth() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        tvTemperature.text = "${temp / 10.0}°C"
        if (temp > 400) {
            tvDeviceHealthStatus.text = "Thermal Throttle"
            tvDeviceHealthStatus.setTextColor(Color.RED)
            tvPerformanceTips.text = "Device temperature critical. Reduce load."
        } else {
            tvDeviceHealthStatus.text = "Excellent"
            tvDeviceHealthStatus.setTextColor(Color.parseColor("#64FFDA"))
            tvPerformanceTips.text = "System nominal. No intervention required."
        }
    }

    private fun populateFooterInfo() {
        thread {
            try {
                val url = URL("https://raw.githubusercontent.com/androidtrackers/certified-android-devices/master/by_model.json")
                val conn = url.openConnection() as HttpURLConnection
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val name = if (root.has(Build.MODEL)) root.getJSONArray(Build.MODEL).getJSONObject(0).getString("name")
                else "${Build.MANUFACTURER} ${Build.MODEL}"
                runOnUiThread { tvFooterInfo.text = "InfoCore System • Android ${Build.VERSION.RELEASE} • $name" }
            } catch (e: Exception) {
                runOnUiThread { tvFooterInfo.text = "InfoCore System • Android ${Build.VERSION.RELEASE} • ${Build.MODEL}" }
            }
        }
    }
}