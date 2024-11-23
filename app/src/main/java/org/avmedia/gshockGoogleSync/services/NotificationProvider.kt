package org.avmedia.gshockGoogleSync.services

//noinspection SuspiciousImport
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationProvider @Inject constructor(
    @ApplicationContext private val appContext: Context // Inject application context
) {

    companion object {
        private const val CHANNEL_ID = "G_SHOCK_SMART_SYNC_CHANNEL"
    }

    fun createNotification(
        title: String,
        notificationText: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val name = "G-Shock Smart Sync"
        val descriptionText =
            "This channel provides notifications for the Casio G-Shock Smart Sync App"
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_day) // Replace with your app's icon
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
