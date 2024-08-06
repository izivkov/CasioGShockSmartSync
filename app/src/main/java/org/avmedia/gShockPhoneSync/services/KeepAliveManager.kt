package org.avmedia.gShockPhoneSync.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.avmedia.gShockPhoneSync.MainActivity

/*
Note, this is WIP, does not work
 */

class KeepAliveManager : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        // Your long-running task here
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE)

        return NotificationCompat.Builder(this, "channel_id")
            .setContentTitle("G-Shock Smart Sync Running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        fun start(context: Context) {
            val startIntent = Intent(context, KeepAliveManager::class.java)
            context.startService(startIntent)
        }

        fun stop(context: Context) {
            val stopIntent = Intent(context, KeepAliveManager::class.java)
            context.stopService(stopIntent)
        }
    }
}
