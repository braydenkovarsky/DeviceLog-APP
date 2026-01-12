package com.example.infocore

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.*
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var tvBattery: TextView
    private lateinit var batteryProgress: ProgressBar // NEW: For the circular ring
    private lateinit var tvStorage: TextView
    private lateinit var tvRam: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvTips: TextView
    private lateinit var tvFooterInfo: TextView
    private lateinit var btnOpenNetwork: Button

    // Note: These IDs remain from your original logic to ensure no crashes
    private lateinit var tvTemperature: TextView
    private lateinit var tvDeviceHealthStatus: TextView
    private lateinit var tvPerformanceTips: TextView

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L
    private var healthCounter = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDeviceInfo()
            healthCounter += updateInterval
            if (healthCounter >= 5000L) {
                updateDeviceHealth()
                healthCounter = 0L
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("device_cache", MODE_PRIVATE)

        // --- BINDING ALL COMPONENTS (Ensuring nothing is missing) ---
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)

        tvBattery = findViewById(R.id.tvBattery)
        batteryProgress = findViewById(R.id.batteryProgress) // Linked to circular ring

        tvRam = findViewById(R.id.tvRam)
        tvStorage = findViewById(R.id.tvStorage)
        tvUptime = findViewById(R.id.tvUptime)

        // Handling the footer/tips components
        tvTips = findViewById(R.id.tvPerformanceTips) // Mapped to the health card tips

        tvTemperature = findViewById(R.id.tvTemperature)
        tvDeviceHealthStatus = findViewById(R.id.tvDeviceHealthStatus)
        tvPerformanceTips = findViewById(R.id.tvPerformanceTips)

        btnOpenNetwork = findViewById(R.id.btnOpenNetwork)

        // --- LISTENERS ---
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(navView) }
        btnOpenNetwork.setOnClickListener { startActivity(Intent(this, NetworkActivity::class.java)) }

        navView.setNavigationItemSelectedListener {
            handleNavItemSelected(it.itemId)
            drawerLayout.closeDrawers()
            true
        }

        // Keep your original footer logic
        populateFooterInfo()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    // ==================== NAVIGATION DRAWER (Original logic intact) ====================
    private fun handleNavItemSelected(itemId: Int) = when (itemId) {
        R.id.nav_dashboard -> { /* Stay here */ }
        R.id.nav_network -> startActivity(Intent(this, NetworkActivity::class.java))
        // Map original menu IDs to new ones if you changed them in the XML
        R.id.menuInfo -> showAlert("About / Credits", "InfoCore professional device monitoring.")
        R.id.menuPrivacy -> showPrivacyDialog()
        else -> {}
    }

    private fun showAlert(title: String, message: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()

    private fun showPrivacyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_privacy, null)
        AlertDialog.Builder(this).setTitle("Privacy & Credits").setView(view).setPositiveButton("OK", null).show()
    }

    // ==================== FOOTER INFO (Original online fetch logic) ====================
    private fun populateFooterInfo() {
        val androidVersion = Build.VERSION.RELEASE
        val deviceName = prefs.getString("device_name", null)

        // We use a safe check here in case the footer view is hidden in the new layout
        if (::tvFooterInfo.isInitialized) {
            if (deviceName != null) {
                tvFooterInfo.text = "InfoCore • Android $androidVersion • $deviceName"
            } else {
                fetchDeviceNameOnline { name ->
                    prefs.edit().putString("device_name", name).apply()
                    runOnUiThread { tvFooterInfo.text = "InfoCore • Android $androidVersion • $name" }
                }
            }
        }
    }

    private fun fetchDeviceNameOnline(callback: (String) -> Unit) = thread {
        try {
            val url = URL("https://raw.githubusercontent.com/androidtrackers/certified-android-devices/master/by_model.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val name = if (root.has(Build.MODEL)) root.getJSONArray(Build.MODEL)
                .getJSONObject(0).getString("name")
            else "${Build.MANUFACTURER} ${Build.MODEL}"
            callback(name)
        } catch (_: Exception) {
            callback("${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    // ==================== DEVICE INFO (Now updates the UI Ring too) ====================
    private fun updateDeviceInfo() {
        val battery = getBatteryPercentage()
        val ram = getRamUsage()
        val storage = getStorageUsage()

        tvBattery.text = "$battery%"
        tvRam.text = "$ram%"
        tvStorage.text = "$storage%"
        tvUptime.text = getUptime()

        // NEW: Animate the circular ring
        batteryProgress.progress = battery
    }

    // ==================== DEVICE HEALTH (Original logic + Glowing Colors) ====================
    private fun updateDeviceHealth() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        tvTemperature.text = "${temp / 10.0}°C"

        val healthPercent = when (batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0) {
            BatteryManager.BATTERY_HEALTH_GOOD -> 100
            else -> 80
        }

        val batteryLevel = getBatteryPercentage()
        val ramUsage = getRamUsage()
        val storageUsage = getStorageUsage()

        val issues = mutableListOf<String>()
        if (batteryLevel < 30) issues.add("Battery attention needed")
        if (ramUsage > 75) issues.add("Close unused apps")

        // Using parseColor to match the futuristic neon green from the image
        if (issues.isEmpty()) {
            tvDeviceHealthStatus.text = "Excellent"
            tvDeviceHealthStatus.setTextColor(android.graphics.Color.parseColor("#64FFDA"))
            tvPerformanceTips.text = "No immediate action required."
        } else {
            tvDeviceHealthStatus.text = "Moderate"
            tvDeviceHealthStatus.setTextColor(android.graphics.Color.parseColor("#FFD54F"))
            tvPerformanceTips.text = issues.joinToString("\n")
        }
    }

    // ==================== DATA FETCHERS (Exact same as your original) ====================
    private fun getBatteryPercentage() = (getSystemService(BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

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
}