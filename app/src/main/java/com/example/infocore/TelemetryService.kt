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
        // 1. Create the initial notification
        startForeground(NOTIF_ID, createNotification("System: Monitoring", "Initializing...", null))

        // 2. Start the telemetry loop
        handler.post(updateRunnable)

        // CRITICAL FIX: Changed from START_STICKY to START_NOT_STICKY
        // This prevents Android from auto-restarting the service after you terminate it.
        return START_NOT_STICKY
    }

    private fun collectHardwareStats(): Bundle {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = (level / scale.toFloat() * 100).toInt()
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        // SAMSUNG PPS CALCULATION LOGIC
        val rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        // Detect uA vs mA scale (100k threshold)
        var currentmA = if (abs(rawCurrent) > 100000) (rawCurrent / 1000).toInt() else rawCurrent.toInt()

        // Force Polarity based on official status
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            currentmA = abs(currentmA)
        } else if (status == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            currentmA = -abs(currentmA)
        }

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

        // Title based on thermal thresholds
        val healthStatus = when {
            tempRaw >= 450 -> "CRITICAL"
            tempRaw >= 380 -> "WARM"
            else -> "OPTIMAL"
        }

        val maPrefix = if (ma > 0) "+" else ""
        val chargingLabel = if (isPlugged) "Charging" else "Discharging"
        val statusColor = if (isPlugged) "#64FFDA" else null

        val title = "InfoCore: $healthStatus"
        val content = "$chargingLabel | $maPrefix$ma mA | $pct% | $tempC°C"

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, createNotification(title, content, statusColor))
    }

    private fun createNotification(title: String, content: String, colorHex: String?): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        // The Terminate action
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (colorHex != null) {
            builder.setColor(Color.parseColor(colorHex))
            builder.setColorized(true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        // Stop the loop immediately
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}