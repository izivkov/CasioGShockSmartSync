package org.avmedia.gShockPhoneSync.ui.actions

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getSystemService
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext

object NotificationProvider {
    val CHANNEL_ID = "G_SHOCK_SMART_SYNC_CHANNEL"

    init {
    }

    fun createNotification(context: Context, title: String, notificationText: String, importance:Int = NotificationManager.IMPORTANCE_DEFAULT) {
        val notificationManager: NotificationManager =
            applicationContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val name = "G-Shock Smart Sync"
        val descriptionText = "This channel provides notifications for the Casio G-Shock Smart Sync App"
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_day)
            .setContentTitle(title)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setAutoCancel(true)

        // Show the Notification
        notificationManager.notify(1, builder.build())
    }
}