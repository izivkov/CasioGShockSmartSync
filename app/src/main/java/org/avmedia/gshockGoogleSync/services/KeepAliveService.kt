package org.avmedia.gshockGoogleSync.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.avmedia.gshockGoogleSync.R
import timber.log.Timber

const val CHANNEL_ID = "KeepAliveServiceChannel"
const val NOTIFICATION_ID = 1

// Foreground Service
@AndroidEntryPoint
class KeepAliveService : LifecycleService() {
    private data class ServiceState(
        val wakeLock: PowerManager.WakeLock? = null,
        val isRunning: Boolean = false
    )

    private val _state = MutableStateFlow(ServiceState())

    override fun onCreate() {
        super.onCreate()
        _state.value = _state.value.copy(
            wakeLock = createWakeLock(),
            isRunning = true
        )
        initializeService()
    }

    private fun initializeService() {
        createNotificationChannel()
        startWithNotification()
    }

    private fun createWakeLock(): PowerManager.WakeLock =
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepAlive::WakeLock")
            .apply { acquire(7 * 24 * 60 * 60 * 1000L) }

    private fun startWithNotification() {
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.g_shock_smart_sync_is_running))
        .setContentText(getString(R.string.preventing_the_app_from_closing))
        .setSmallIcon(R.drawable.ic_watch_face)
        .build()

    private fun createNotificationChannel() {
        NotificationChannel(
            CHANNEL_ID,
            "Keep Alive Service",
            NotificationManager.IMPORTANCE_LOW
        ).let { channel ->
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupService()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        cleanupService()
    }

    private fun cleanupService() {
        _state.value.wakeLock?.let { wakelock ->
            if (wakelock.isHeld) wakelock.release()
        }
        _state.value = ServiceState()
    }

    companion object {
        fun startService(context: Context) = runCatching {
            Intent(context, KeepAliveService::class.java)
                .let { context.startForegroundService(it) }
        }.onFailure { e ->
            Timber.e("Failed to start KeepAliveService: ${e.message}")
        }

        fun stopService(context: Context) = runCatching {
            Intent(context, KeepAliveService::class.java)
                .let { context.stopService(it) }
        }.onFailure { e ->
            Timber.e("Failed to stop KeepAliveService: ${e.message}")
        }
    }
}
