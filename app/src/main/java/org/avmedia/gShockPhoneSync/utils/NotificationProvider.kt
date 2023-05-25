package org.avmedia.gShockPhoneSync.utils

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext

object NotificationProvider {
    val CHANNEL_ID = "G_SHOCK_SMART_SYNC_CHANNEL"

    fun createNotification(
        context: Context,
        title: String,
        notificationText: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val notificationManager: NotificationManager =
            applicationContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val name = "G-Shock Smart Sync"
        val descriptionText =
            "This channel provides notifications for the Casio G-Shock Smart Sync App"
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system

        // From ChatGPT:
        // There is no harm or memory leak from calling createNotificationChannel() multiple times
        // with the same channel ID. The second call will simply overwrite the first call.
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_day)
            .setContentTitle(title)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notificationText)
            )
            .setAutoCancel(true)

        // Show the Notification
        notificationManager.notify(1, builder.build())
    }
}