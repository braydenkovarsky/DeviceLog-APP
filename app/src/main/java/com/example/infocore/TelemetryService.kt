package com.example.infocore

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TelemetryService : Service() {

    private val CHANNEL_ID = "LIVE_TELEMETRY"
    private val NOTIF_ID = 1001

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Extract Data from MainActivity's logic
        val label = intent?.getStringExtra("LABEL") ?: "Monitoring"
        val mA = intent?.getStringExtra("MA") ?: "0 mA"
        val pct = intent?.getIntExtra("PCT", 0) ?: 0
        val temp = intent?.getStringExtra("TEMP") ?: "0.0°C"
        val healthStatus = intent?.getStringExtra("HEALTH_STATUS") ?: "STABLE"
        val colorHex = intent?.getStringExtra("COLOR") ?: "#64FFDA"

        // 2. Build Interaction Intents
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply { action = "STOP_MONITOR" }
        val stopPendingIntent = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        // 3. Create the Forensic-Style Notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // More "system" style icon
            .setContentTitle("System: $healthStatus")
            .setContentText("$label | $mA | $pct% | $temp")
            .setSubText("InfoCore Live Telemetry")
            .setOngoing(true)
            .setSilent(true) // Keeps it out of the way but visible
            .setColorized(true)
            .setColor(Color.parseColor(colorHex))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Avoids intrusive pop-ups every second
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Terminate Monitor", stopPendingIntent)
            .build()

        // 4. Start as Foreground Service (Android 14+ compliant)
        startForeground(NOTIF_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}