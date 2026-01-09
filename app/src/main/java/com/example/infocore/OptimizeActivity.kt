package com.example.infocore

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class OptimizeActivity : AppCompatActivity() {

    // UI
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var btnStartOptimize: Button

    // Report saving
    private var lastReport: String = ""
    private val saveFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null && lastReport.isNotEmpty()) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(lastReport.toByteArray()) }
                    Toast.makeText(this, "Report saved successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to save report", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimize)

        // Initialize UI
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navigationView)
        topAppBar = findViewById(R.id.topAppBar)
        btnStartOptimize = findViewById(R.id.btnStartOptimize)

        // Hamburger menu opens drawer
        topAppBar.setNavigationOnClickListener { drawerLayout.openDrawer(navView) }

        // Drawer item clicks
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menuInfo -> AlertDialog.Builder(this)
                    .setTitle("About / Credits")
                    .setMessage(
                        "InfoCore is a professional device monitoring application.\n" +
                                "It provides device information such as battery, RAM, storage, and uptime.\n" +
                                "All operations are read-only and do not modify your device."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                R.id.menuPrivacy -> AlertDialog.Builder(this)
                    .setTitle("Privacy & Policy")
                    .setMessage(
                        "InfoCore does NOT modify your device.\n" +
                                "Only reads stats: battery, RAM, storage, uptime.\n" +
                                "No personal data is collected or shared."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                R.id.menuOptimize -> drawerLayout.closeDrawers() // current page
            }
            drawerLayout.closeDrawers()
            true
        }

        // Start button
        btnStartOptimize.setOnClickListener { performDeviceAnalysis() }
    }

    // ===== Device Analysis =====
    private fun performDeviceAnalysis() {
        val reportBuilder = StringBuilder()
        reportBuilder.appendLine("🔍 Starting full device analysis…\n")

        // Device Info
        reportBuilder.appendLine("=== Device Info ===")
        reportBuilder.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        reportBuilder.appendLine("Model: ${Build.MODEL}")
        reportBuilder.appendLine("Android Version: ${Build.VERSION.RELEASE}")
        reportBuilder.appendLine("SDK: ${Build.VERSION.SDK_INT}")

        // Memory Info
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        reportBuilder.appendLine("\n=== Memory ===")
        reportBuilder.appendLine("Total RAM: ${memInfo.totalMem / (1024 * 1024)} MB")
        reportBuilder.appendLine("Available RAM: ${memInfo.availMem / (1024 * 1024)} MB")
        reportBuilder.appendLine("Low Memory?: ${memInfo.lowMemory}")

        // Storage Info
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val totalStorage = stat.blockCountLong * stat.blockSizeLong
        val freeStorage = stat.availableBlocksLong * stat.blockSizeLong
        reportBuilder.appendLine("\n=== Storage ===")
        reportBuilder.appendLine("Total Storage: ${totalStorage / (1024 * 1024)} MB")
        reportBuilder.appendLine("Available Storage: ${freeStorage / (1024 * 1024)} MB")
        reportBuilder.appendLine("Used Storage: ${(totalStorage - freeStorage) / (1024 * 1024)} MB")

        // Network Info
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netCapabilities = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        val networkType = when {
            netCapabilities == null -> "No Connection"
            netCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            netCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            else -> "Other"
        }
        reportBuilder.appendLine("\n=== Network ===")
        reportBuilder.appendLine("Connection Type: $networkType")

        // Nerdy Details
        reportBuilder.appendLine("\n=== Nerdy Details ===")
        reportBuilder.appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        reportBuilder.appendLine("Bootloader: ${Build.BOOTLOADER}")
        reportBuilder.appendLine("Hardware: ${Build.HARDWARE}")
        reportBuilder.appendLine("Device: ${Build.DEVICE}")
        reportBuilder.appendLine("Fingerprint: ${Build.FINGERPRINT}")
        reportBuilder.appendLine("Brand: ${Build.BRAND}")
        reportBuilder.appendLine("Product: ${Build.PRODUCT}")
        reportBuilder.appendLine("Display: ${Build.DISPLAY}")
        reportBuilder.appendLine("Board: ${Build.BOARD}")

        reportBuilder.appendLine("\n✅ Analysis Complete!")

        lastReport = reportBuilder.toString()
        showReportPopup(lastReport)
    }

    // ===== Popup for report =====
    private fun showReportPopup(report: String) {
        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.text = report
        textView.setPadding(32, 32, 32, 32)
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Device Analysis Report")
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Save Report") { _, _ ->
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                saveFileLauncher.launch("DeviceReport_$timeStamp.txt")
            }
            .show()
    }
}
