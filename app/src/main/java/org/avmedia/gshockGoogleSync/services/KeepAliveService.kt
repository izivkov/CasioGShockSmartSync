package org.avmedia.gshockGoogleSync.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import timber.log.Timber

class KeepAliveService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "keep_alive_channel"

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Starting the service failing should never take the app down.
                CrashReportHelper.logCrash(context, e, "KeepAliveService.start failed")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                CrashReportHelper.logCrash(context, e, "KeepAliveService.stop failed")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager == null) {
                CrashReportHelper.logCrash(
                    applicationContext,
                    IllegalStateException("NotificationManager unavailable"),
                    "KeepAliveService.onCreate"
                )
                stopSelf()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "G-Shock Connection",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("G-Shock Smart Sync")
                .setContentText("Connected to watch")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Whatever went wrong, don't let it crash the whole app process.
            CrashReportHelper.logCrash(applicationContext, e, "KeepAliveService.onCreate")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Explicit, rather than relying on the base class default (START_STICKY).
        // If the process is killed, the app's own reconnect logic decides whether
        // to restart this, instead of the OS restarting it with a null Intent
        // outside of any activity/app context.
        return START_NOT_STICKY
    }

    // API 34+: called when the OS force-stops a long-running dataSync foreground
    // service after its ~6h time limit. Without overriding this, the service
    // still gets stopped by the system anyway — but other code assuming it's
    // always alive can be caught off guard. Override lets us stop cleanly and
    // record that it happened.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.i("KeepAliveService.onTimeout: foreground service time limit reached (fgsType=$fgsType)")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}