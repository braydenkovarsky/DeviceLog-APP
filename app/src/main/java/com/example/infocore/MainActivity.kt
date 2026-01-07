package com.example.infocore

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
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

    private lateinit var btnOpenNetwork: Button

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval: Long = 2000 // 2 seconds

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDeviceInfo()
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)

        tvBattery = findViewById(R.id.tvBattery)
        tvStorage = findViewById(R.id.tvStorage)
        tvRam = findViewById(R.id.tvRam)
        tvUptime = findViewById(R.id.tvUptime)
        tvTips = findViewById(R.id.tvTips)
        btnOpenNetwork = findViewById(R.id.btnOpenNetwork)

        // Drawer toggle
        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(navView)
        }

        // Navigation drawer items
        navView.setNavigationItemSelectedListener { item ->
            when(item.itemId) {
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
                    val inflater = layoutInflater
                    val view = inflater.inflate(R.layout.dialog_privacy, null)
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
            drawerLayout.closeDrawers()
            true
        }

        // Button to open NetworkActivity
        btnOpenNetwork.setOnClickListener {
            startActivity(Intent(this, NetworkActivity::class.java))
        }

        // Start live updates
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateDeviceInfo() {
        // Battery
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else 0
        tvBattery.text = "Battery: $batteryPct%"

        // RAM
        val memInfo = ActivityManager.MemoryInfo()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memInfo)
        val ramUsage = ((memInfo.totalMem - memInfo.availMem).toFloat() / memInfo.totalMem * 100).roundToInt()
        tvRam.text = "RAM Usage: $ramUsage%"

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val storageUsage = ((total - free).toFloat() / total * 100).roundToInt()
        tvStorage.text = "Storage: $storageUsage%"

        // Uptime
        val uptimeMillis = SystemClock.elapsedRealtime()
        val hours = (uptimeMillis / 1000 / 3600).toInt()
        val minutes = (uptimeMillis / 1000 / 60 % 60).toInt()
        val seconds = (uptimeMillis / 1000 % 60).toInt()
        tvUptime.text = "Device Uptime: ${hours}h ${minutes}m ${seconds}s"

        // Tips
        val tips = StringBuilder()
        if (ramUsage > 70) tips.append("Close unused apps.\n")
        if (storageUsage > 80) tips.append("Free up storage.\n")
        if (batteryPct < 30) tips.append("Charge your device.\n")
        if (tips.isEmpty()) tips.append("Your device is running well!")
        tvTips.text = "Tips:\n$tips"
    }
}
