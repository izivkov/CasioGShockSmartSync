package org.avmedia.gShockPhoneSync.utils

//noinspection SuspiciousImport
import android.R
import android.app.Notification
import android.app.Notification.Builder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.avmedia.gShockPhoneSync.MainActivity

class ForegroundService : Service() {
    @Suppress(
        "PrivatePropertyName"
    )
    private val CHANNEL_ID: String = "CHANNEL_ONE"
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val input = intent.getStringExtra("inputExtra")
        createNotificationChannel()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(
                this,
                MainActivity::class.java
            ), PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Waiting for Connections")
            .setContentText(input)
            .setSmallIcon(R.drawable.ic_menu_day)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        // here is the code you wanna run in background
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "G-Shock Sync foreground service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }
}