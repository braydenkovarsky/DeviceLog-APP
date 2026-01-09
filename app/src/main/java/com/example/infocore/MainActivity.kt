package com.example.infocore

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

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
    private lateinit var prefs: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval: Long = 1000 // 1 second updates

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

        // Drawer toggle
        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(navView)
        }

        // Navigation drawer items
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menuInfo -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("About / Credits")
                        .setMessage(
                            "InfoCore is a professional device monitoring application.\n" +
                                    "It provides device information such as battery, RAM, storage, and uptime.\n" +
                                    "All operations are read-only and do not modify your device."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }

                R.id.menuPrivacy -> {
                    val view = layoutInflater.inflate(R.layout.dialog_privacy, null)
                    val tvContent = view.findViewById<TextView>(R.id.tvPolicyContent)
                    tvContent.text = """
                        Privacy & Credits Policy

                        InfoCore does NOT modify your device.
                        Only reads stats: battery, RAM, storage, uptime.
                        No personal data is collected or shared.

                        Credits:
                        Developed by InfoCore Team.
                    """.trimIndent()

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Privacy & Credits")
                        .setView(view)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            // Close drawer AFTER handling click
            drawerLayout.closeDrawers()
            true // important: consume the click
        }

        // Button to open NetworkActivity
        btnOpenNetwork.setOnClickListener {
            startActivity(Intent(this, NetworkActivity::class.java))
        }

        // Populate footer device info
        populateFooterInfo()

        // Start 1-second updates
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateDeviceInfo() {
        // Battery
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        tvBattery.text = "$batteryPct%"

        // RAM
        val memInfo = ActivityManager.MemoryInfo()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.getMemoryInfo(memInfo)
        val ramUsage =
            ((memInfo.totalMem - memInfo.availMem).toFloat() / memInfo.totalMem * 100).roundToInt()
        tvRam.text = "$ramUsage%"

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val storageUsage =
            ((total - free).toFloat() / total * 100).roundToInt()
        tvStorage.text = "$storageUsage%"

        // Uptime
        val uptimeMillis = SystemClock.elapsedRealtime()
        val h = uptimeMillis / 1000 / 3600
        val m = uptimeMillis / 1000 / 60 % 60
        val s = uptimeMillis / 1000 % 60
        tvUptime.text = "${h}h ${m}m ${s}s"

        // Tips
        val tips = StringBuilder()
        if (ramUsage > 70) tips.append("Close unused apps.\n")
        if (storageUsage > 80) tips.append("Free up storage.\n")
        if (batteryPct < 30) tips.append("Charge your device.\n")
        if (tips.isEmpty()) tips.append("Your device is running well!")
        tvTips.text = tips.toString()
    }

    private fun populateFooterInfo() {
        val cached = prefs.getString("device_name", null)
        val androidVersion = Build.VERSION.RELEASE

        if (cached != null) {
            tvFooterInfo.text = "InfoCore • Android $androidVersion • $cached"
            return
        }

        tvFooterInfo.text = "InfoCore • Android $androidVersion • Detecting device…"

        fetchDeviceNameOnline { name ->
            prefs.edit().putString("device_name", name).apply()
            runOnUiThread {
                tvFooterInfo.text = "InfoCore • Android $androidVersion • $name"
            }
        }
    }

    private fun fetchDeviceNameOnline(callback: (String) -> Unit) {
        thread {
            try {
                val model = Build.MODEL
                val manufacturer = Build.MANUFACTURER

                val url = URL(
                    "https://raw.githubusercontent.com/androidtrackers/certified-android-devices/master/by_model.json"
                )

                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)

                if (root.has(model)) {
                    val arr = root.getJSONArray(model)
                    val obj = arr.getJSONObject(0)
                    callback(obj.getString("name"))
                } else {
                    callback("$manufacturer $model")
                }
            } catch (e: Exception) {
                callback("${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }
    }
}
