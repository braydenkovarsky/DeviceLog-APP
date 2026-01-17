package com.example.infocore

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class TelemetryService : Service() {

    private val CHANNEL_ID = "LIVE_TELEMETRY"
    private val NOTIF_ID = 1001
    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            val stats = collectHardwareStats()
            updateForegroundNotification(stats)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, createNotification("System: Monitoring", "Initializing...", null))
        handler.post(updateRunnable)
        return START_STICKY
    }

    private fun collectHardwareStats(): Bundle {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = (level / scale.toFloat() * 100).toInt()
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toDouble()
        // Convert microAmps to milliAmps and ensure we get the absolute value for custom formatting
        val currentmA = if (abs(rawCurrent) > 100000) abs(rawCurrent / 1000).toInt() else abs(rawCurrent).toInt()

        return Bundle().apply {
            putInt("PCT", pct)
            putInt("TEMP", rawTemp)
            putInt("MA", currentmA)
            putInt("PLUGGED", batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
        }
    }

    private fun updateForegroundNotification(data: Bundle) {
        val pct = data.getInt("PCT")
        val tempRaw = data.getInt("TEMP")
        val tempC = tempRaw / 10.0
        val ma = data.getInt("MA")
        val isPlugged = data.getInt("PLUGGED") != 0

        // 1. Determine Health Status for the Title
        val healthStatus = when {
            tempRaw >= 450 -> "CRITICAL"
            tempRaw >= 400 -> "BAD"
            tempRaw >= 350 -> "POOR"
            tempRaw >= 280 -> "GOOD"
            else -> "EXCELLENT"
        }

        // 2. Charging Logic: Set Color and mA Prefix (+ or -)
        val statusColor: String?
        val maPrefix: String
        val chargingLabel: String

        if (isPlugged) {
            statusColor = "#00E676" // Green for charging
            maPrefix = "+"
            chargingLabel = "Charging"
        } else {
            statusColor = null // No color for discharging (System default)
            maPrefix = "-"
            chargingLabel = "Discharging"
        }

        val title = "System: $healthStatus"
        val content = "$chargingLabel | $maPrefix$ma mA | $pct% | $tempC°C"

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, createNotification(title, content, statusColor))
    }

    private fun createNotification(title: String, content: String, colorHex: String?): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply { action = "STOP_MONITOR" }
        val stopPendingIntent = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "TERMINATE", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Only apply color if colorHex is not null
        if (colorHex != null) {
            builder.setColor(Color.parseColor(colorHex))
            builder.setColorized(true)
        } else {
            builder.setColorized(false) // Reverts to system standard
        }

        return builder.build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}