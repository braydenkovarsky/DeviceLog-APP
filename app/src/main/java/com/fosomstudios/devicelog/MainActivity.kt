package com.fosomstudios.devicelog

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.webkit.WebView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.ads.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var tvBattery: TextView
    private lateinit var batteryProgress: ProgressBar
    private lateinit var tvChargingSpeed: TextView
    private lateinit var tvChargingType: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvRam: TextView
    private lateinit var tvStorage: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvFooterInfo: TextView
    private lateinit var tvDeviceHealthStatus: TextView
    private lateinit var tvNotificationWarning: TextView

    // NEW BUTTON
    private lateinit var btnSubmitBug: Button

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var mainDashboardContainer: View

    // Ad Variables
    private lateinit var adView: AdView
    private lateinit var adContainer: View

    private val handler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            refreshDisplay()
            updateStatusUI()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdMob Engine
        MobileAds.initialize(this) {}

        initViews()

        setSupportActionBar(topAppBar)
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(navView) }

        setupMasterAnimations()
        createNotificationChannels()

        // Sync service on startup
        checkAndSyncServiceState()
        fetchFriendlyDeviceName()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        mainDashboardContainer = findViewById(R.id.mainDashboardContainer)
        tvBattery = findViewById(R.id.tvBattery)
        batteryProgress = findViewById(R.id.batteryProgress)
        tvRam = findViewById(R.id.tvRam)
        tvStorage = findViewById(R.id.tvStorage)
        tvUptime = findViewById(R.id.tvUptime)
        tvFooterInfo = findViewById(R.id.tvFooterInfo)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvDeviceHealthStatus = findViewById(R.id.tvDeviceHealthStatus)
        tvChargingSpeed = findViewById(R.id.tvChargingSpeed)
        tvChargingType = findViewById(R.id.tvChargingType)
        tvNotificationWarning = findViewById(R.id.tvNotificationWarning)

        // LINK THE NEW VISIBLE BUTTON
        btnSubmitBug = findViewById(R.id.btnSubmitBug)

        // Ad UI Setup
        adContainer = findViewById(R.id.adContainer)
        adView = findViewById(R.id.adView)
        val btnCloseAd = findViewById<ImageButton>(R.id.btnCloseAd)

        btnCloseAd.setOnClickListener {
            adContainer.visibility = View.GONE
        }

        // Monitoring Toggle restored
        tvNotificationWarning.setOnClickListener {
            handleMonitoringToggle()
        }

        // Submit Bug Feature [CLICK LISTENER ON BUTTON NOW]
        btnSubmitBug.setOnClickListener {
            submitBugReport()
        }

        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_dashboard -> drawerLayout.closeDrawers()
                R.id.nav_network -> {
                    loadBannerAd()
                    startActivity(Intent(this, NetworkActivity::class.java))
                    drawerLayout.closeDrawers()
                }
                R.id.menuInfo -> showAboutDialog()
                R.id.menuPrivacy -> showPrivacyPolicyDialog()
            }
            true
        }
    }

    // --- BUG REPORTING ENGINE ---
    private fun submitBugReport() {
        val reportTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val reportBody = """
            Device Log Report
            ---------------------------
            Time: $reportTime
            Device: $deviceName
            Android SDK: ${Build.VERSION.SDK_INT}
            
            [TELEMETRY SNAPSHOT]
            Battery Level: ${tvBattery.text}
            Charging Status: ${tvChargingType.text}
            Current Flow: ${tvChargingSpeed.text}
            Temperature: ${tvTemperature.text}
            RAM Usage: ${tvRam.text}
            Storage Usage: ${tvStorage.text}
            Uptime: ${tvUptime.text}
            Health Status: ${tvDeviceHealthStatus.text}
            
            [BUG DESCRIPTION]
            Please describe the issue you are facing below:
            
            
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("fosomstudios@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Device log report")
            putExtra(Intent.EXTRA_TEXT, reportBody)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found to submit report.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- AD ENGINE ---
    private fun loadBannerAd() {
        val adRequest = AdRequest.Builder().build()
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() { adContainer.visibility = View.VISIBLE }
            override fun onAdFailedToLoad(adError: LoadAdError) { adContainer.visibility = View.GONE }
        }
        adView.loadAd(adRequest)
    }

    // --- PERMISSION & SERVICE HANDSHAKE ---

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun checkAndSyncServiceState() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val userWantsBg = prefs.getBoolean("user_wants_bg", true)

        if (userWantsBg && hasNotificationPermission()) {
            startTelemetryService()
        } else if (!hasNotificationPermission()) {
            stopService(Intent(this, TelemetryService::class.java))
        }
    }

    private fun handleMonitoringToggle() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val currentlyOn = prefs.getBoolean("user_wants_bg", true)

        if (!currentlyOn) {
            if (hasNotificationPermission()) {
                enableServiceFlow()
            } else {
                showPermissionRequiredDialog()
            }
        } else {
            prefs.edit().putBoolean("user_wants_bg", false).apply()
            stopService(Intent(this, TelemetryService::class.java))
            updateStatusUI()
            invalidateOptionsMenu()
        }
    }

    private fun showPermissionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Grant notification permissions to enable background telemetry.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent().apply {
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enableServiceFlow() {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putBoolean("user_wants_bg", true).apply()
        startTelemetryService()
        Toast.makeText(this, "Telemetry Active", Toast.LENGTH_SHORT).show()
        updateStatusUI()
        invalidateOptionsMenu()
    }

    // --- TELEMETRY ENGINE ---

    private fun refreshDisplay() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentmA = if (abs(rawCurrent) > 100000) (rawCurrent / 1000).toInt() else rawCurrent.toInt()

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            currentmA = abs(currentmA)
            tvChargingSpeed.setTextColor(Color.parseColor("#64FFDA"))
            tvChargingSpeed.text = "+$currentmA mA"
        } else {
            currentmA = -abs(currentmA)
            tvChargingSpeed.setTextColor(Color.parseColor("#FF5252"))
            tvChargingSpeed.text = "$currentmA mA"
        }

        // Trickle Charging Logic [2026-01-22] - Restored
        var label = "Discharging"
        var color = "#FF5252"
        if (plugged != 0) {
            label = if (level >= 100) "Trickle Charging" else "Standard Charging"
            color = if (level >= 100) "#64FFDA" else "#FFB74D"
        }

        tvChargingType.text = label
        tvChargingType.setTextColor(Color.parseColor(color))

        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        tvBattery.text = "$level%"
        batteryProgress.progress = level
        tvTemperature.text = "${rawTemp / 10.0}°C"
        tvRam.text = "${getRamUsage()}%"
        tvStorage.text = "${getStorageUsage()}%"
        tvUptime.text = getUptime()
        updateHealthWarnings(rawTemp)
    }

    private fun getRamUsage(): Int {
        val mem = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        return ((mem.totalMem - mem.availMem).toDouble() / mem.totalMem * 100).roundToInt()
    }

    private fun getStorageUsage(): Int {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return if (total <= 0L) 0 else ((total - free).toDouble() / total * 100).roundToInt()
    }

    private fun getUptime(): String {
        val uptime = SystemClock.elapsedRealtime() / 1000
        return "${uptime / 3600}h ${(uptime % 3600) / 60}m ${uptime % 60}s"
    }

    private fun updateHealthWarnings(temp: Int) {
        if (temp >= 450) {
            tvDeviceHealthStatus.text = "CRITICAL"
            tvDeviceHealthStatus.setTextColor(Color.parseColor("#FF1744"))
        } else {
            tvDeviceHealthStatus.text = "OPTIMAL"
            tvDeviceHealthStatus.setTextColor(Color.parseColor("#64FFDA"))
        }
    }

    // --- LEGAL & SYSTEM OVERLAYS ---

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val active = prefs.getBoolean("user_wants_bg", true) && hasNotificationPermission()
        val item = menu?.add(Menu.NONE, 1001, Menu.NONE, if (active) "ACTIVE" else "INACTIVE")
        item?.setIcon(if (active) R.drawable.ic_switch_on else R.drawable.ic_switch_off)
        item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) { handleMonitoringToggle(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun updateStatusUI() {
        val active = getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("user_wants_bg", true)
        val isTrulyRunning = active && hasNotificationPermission()
        tvNotificationWarning.text = if (isTrulyRunning) "MONITORING: ACTIVE" else "MONITORING: INACTIVE"
        tvNotificationWarning.setTextColor(Color.parseColor(if (isTrulyRunning) "#64FFDA" else "#FF5252"))
    }

    private fun startTelemetryService() {
        ContextCompat.startForegroundService(this, Intent(this, TelemetryService::class.java))
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("LIVE_TELEMETRY", "Telemetry", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun fetchFriendlyDeviceName() {
        val model = Build.MODEL
        thread {
            var friendlyName = model
            try {
                val json = JSONObject(URL("https://raw.githubusercontent.com/cedric-anne/android-device-names/master/library/src/main/assets/devices.json").readText())
                val devices = json.getJSONArray("devices")
                for (i in 0 until devices.length()) {
                    val d = devices.getJSONObject(i)
                    if (d.getString("model").equals(model, ignoreCase = true)) {
                        friendlyName = d.getString("market_name")
                        break
                    }
                }
            } catch (e: Exception) {}
            handler.post { tvFooterInfo.text = "DeviceLog Telemetry • $friendlyName" }
        }
    }

    private fun setupMasterAnimations() {
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                mainDashboardContainer.translationX = slideOffset * 30f
                mainDashboardContainer.alpha = 1f - (slideOffset * 0.4f)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
        handler.post(uiUpdateRunnable)
        invalidateOptionsMenu()
    }

    override fun onPause() { adView.pause(); super.onPause(); handler.removeCallbacks(uiUpdateRunnable) }
    override fun onDestroy() { adView.destroy(); super.onDestroy() }
}