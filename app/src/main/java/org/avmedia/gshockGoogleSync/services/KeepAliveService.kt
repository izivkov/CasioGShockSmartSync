package org.avmedia.gshockGoogleSync.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import javax.inject.Inject

const val CHANNEL_ID = "KeepAliveServiceChannel"
const val NOTIFICATION_ID = 1

// Foreground Service
@AndroidEntryPoint
class KeepAliveService  : LifecycleService() {

    @Inject
    lateinit var translateApi: TranslateRepository

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(translateApi.getString(this, R.string.g_shock_smart_sync_is_running))
            .setContentText(translateApi.getString(this, R.string.preventing_the_app_from_closing))
            .setSmallIcon(R.drawable.ic_watch_face)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Keep Alive Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepAlive::WakeLock")
        wakeLock?.acquire()
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }
}
