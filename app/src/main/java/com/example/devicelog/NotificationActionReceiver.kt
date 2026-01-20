package com.example.devicelog

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_MONITOR") {
            // Remove the notification
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(1001)

            // This kills the background process immediately
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}