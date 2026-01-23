package com.fosomstudios.devicelog

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
        // 1. Mandatory Android 15 Logic: Start with the correct type flag
        val initialNotification = createNotification("System: Monitoring", "Initializing...", null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires matching the Manifest 'specialUse' type here
            startForeground(NOTIF_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, initialNotification)
        }

        // 2. Start the telemetry loop
        handler.post(updateRunnable)

        return START_NOT_STICKY
    }

    private fun collectHardwareStats(): Bundle {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val pct = (level / scale.toFloat() * 100).toInt()
        val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val rawCurrent = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentmA = if (abs(rawCurrent) > 100000) (rawCurrent / 1000).toInt() else rawCurrent.toInt()

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

        val healthStatus = when {
            tempRaw >= 450 -> "CRITICAL"
            tempRaw >= 380 -> "WARM"
            else -> "OPTIMAL"
        }

        val maPrefix = if (ma > 0) "+" else ""
        val chargingLabel = if (isPlugged) "Charging" else "Discharging"

        // Use a subtle color to indicate active monitoring
        val statusColor = if (isPlugged) "#64FFDA" else "#94A3B8"

        val title = "InfoCore Status: $healthStatus"
        val content = "$chargingLabel • $maPrefix$ma mA • $pct% • $tempC°C"

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
            .setOngoing(true) // Keeps the service from being swiped away
            .setSilent(true)  // No annoying "ding" every second
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "TERMINATE", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Keeps it tidy in the tray
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (colorHex != null) {
            builder.setColor(Color.parseColor(colorHex))
        }

        return builder.build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE) // Clean up the notification on exit
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
