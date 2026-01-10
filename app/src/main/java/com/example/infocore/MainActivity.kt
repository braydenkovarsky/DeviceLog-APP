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
    private lateinit var tvStorage: TextView
    private lateinit var tvRam: TextView
    private lateinit var tvUptime: TextView
    private lateinit var tvTips: TextView
    private lateinit var tvFooterInfo: TextView
    private lateinit var btnOpenNetwork: Button

    // Charging indicator
    private lateinit var layoutCharging: LinearLayout
    private lateinit var imgCharging: ImageView
    private lateinit var tvChargingStatus: TextView

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDeviceInfo()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("device_cache", MODE_PRIVATE)

        // Initialize UI components
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        tvBattery = findViewById(R.id.tvBattery)
        tvStorage = findViewById(R.id.tvStorage)
        tvRam = findViewById(R.id.tvRam)
        tvUptime = findViewById(R.id.tvUptime)
        tvTips = findViewById(R.id.tvTips)
        tvFooterInfo = findViewById(R.id.tvFooterInfo)
        btnOpenNetwork = findViewById(R.id.btnOpenNetwork)

        // Charging indicator
        layoutCharging = findViewById(R.id.layoutCharging)
        imgCharging = findViewById(R.id.imgCharging)
        tvChargingStatus = findViewById(R.id.tvChargingStatus)

        // Listeners
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(navView) }
        btnOpenNetwork.setOnClickListener { startActivity(Intent(this, NetworkActivity::class.java)) }
        navView.setNavigationItemSelectedListener {
            handleNavItemSelected(it.itemId)
            drawerLayout.closeDrawers()
            true
        }

        populateFooterInfo()
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    // ==================== NAVIGATION DRAWER ====================
    private fun handleNavItemSelected(itemId: Int) = when (itemId) {
        R.id.menuInfo -> showAlert(
            "About / Credits",
            "InfoCore is a professional device monitoring application.\n" +
                    "It provides device information such as battery, RAM, storage, and uptime.\n" +
                    "All operations are read-only and do not modify your device."
        )
        R.id.menuPrivacy -> showPrivacyDialog()
        R.id.menuOptimize -> startActivity(Intent(this, OptimizeActivity::class.java))
        else -> {}
    }

    private fun showAlert(title: String, message: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()

    private fun showPrivacyDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_privacy, null)
        view.findViewById<TextView>(R.id.tvPolicyContent).text = """
            Privacy & Credits Policy

            InfoCore does NOT modify your device.
            Only reads stats: battery, RAM, storage, uptime.
            No personal data is collected or shared.

            Credits:
            Developed by InfoCore Team.
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Privacy & Credits")
            .setView(view)
            .setPositiveButton("OK", null)
            .show()
    }

    // ==================== FOOTER INFO ====================
    private fun populateFooterInfo() {
        val androidVersion = Build.VERSION.RELEASE
        prefs.getString("device_name", null)?.let {
            tvFooterInfo.text = "InfoCore • Android $androidVersion • $it"
            return
        }

        tvFooterInfo.text = "InfoCore • Android $androidVersion • Detecting device…"
        fetchDeviceNameOnline { name ->
            prefs.edit().putString("device_name", name).apply()
            runOnUiThread { tvFooterInfo.text = "InfoCore • Android $androidVersion • $name" }
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

    // ==================== DEVICE INFO LOGIC ====================
    private fun updateDeviceInfo() {
        val battery = getBatteryPercentage()
        val ram = getRamUsage()
        val storage = getStorageUsage()
        tvBattery.text = "$battery%"
        tvRam.text = "$ram%"
        tvStorage.text = "$storage%"
        tvUptime.text = getUptime()
        tvTips.text = generateTips(ram, storage, battery)

        // Update charging indicator
        updateChargingStatus()
    }

    private fun updateChargingStatus() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        layoutCharging.visibility = View.VISIBLE

        if (isCharging) {
            tvChargingStatus.text = "Device is charging"
            tvChargingStatus.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
            // Load GIF
            Glide.with(this)
                .asGif()
                .load(R.raw.charging) // res/raw/charging.gif
                .into(imgCharging)
        } else {
            tvChargingStatus.text = "Device is not charging"
            tvChargingStatus.setTextColor(resources.getColor(android.R.color.black, theme))
            // Load static PNG
            Glide.with(this)
                .load(R.drawable.ic_favicon) // res/drawable/ic_favicon.png
                .into(imgCharging)
        }
    }


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
        val s = uptime / 1000 % 60
        return "${h}h ${m}m ${s}s"
    }

    private fun generateTips(ram: Int, storage: Int, battery: Int) = buildString {
        if (ram > 70) append("Close unused apps.\n")
        if (storage > 80) append("Free up storage.\n")
        if (battery < 30) append("Charge your device.\n")
        if (isEmpty()) append("Your device is running well!")
    }
}
