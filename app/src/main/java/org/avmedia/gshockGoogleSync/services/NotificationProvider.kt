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
    @ApplicationContext private val appContext: Context
) {
    data class NotificationConfig(
        val channelId: String = CHANNEL_ID,
        val channelName: String = "G-Shock Smart Sync",
        val channelDescription: String = "This channel provides notifications for the Casio G-Shock Smart Sync App",
        val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        val notificationId: Int = 1
    )

    data class NotificationContent(
        val title: String,
        val text: String,
        val priority: Int = NotificationCompat.PRIORITY_DEFAULT
    )

    sealed interface NotificationResult {
        data object Success : NotificationResult
        data class Error(val exception: Throwable) : NotificationResult
    }

    fun createNotification(content: NotificationContent, config: NotificationConfig = NotificationConfig()): NotificationResult =
        runCatching {
            getNotificationManager()
                .also { manager -> ensureChannelExists(manager, config) }
                .also { manager -> showNotification(manager, content, config) }
        }.fold(
            onSuccess = { NotificationResult.Success },
            onFailure = { NotificationResult.Error(it) }
        )

    private fun getNotificationManager(): NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannelExists(manager: NotificationManager, config: NotificationConfig) {
        NotificationChannel(config.channelId, config.channelName, config.importance)
            .apply { description = config.channelDescription }
            .let { manager.createNotificationChannel(it) }
    }

    private fun showNotification(
        manager: NotificationManager,
        content: NotificationContent,
        config: NotificationConfig
    ) {
        createNotificationBuilder(content, config)
            .build()
            .let { notification -> manager.notify(config.notificationId, notification) }
    }

    private fun createNotificationBuilder(content: NotificationContent, config: NotificationConfig) =
        NotificationCompat.Builder(appContext, config.channelId)
            .setSmallIcon(R.drawable.ic_menu_day)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setPriority(content.priority)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setAutoCancel(true)

    companion object {
        private const val CHANNEL_ID = "G_SHOCK_SMART_SYNC_CHANNEL"
    }
}
