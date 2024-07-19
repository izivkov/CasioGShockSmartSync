package org.avmedia.gShockPhoneSync.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.avmedia.gshockapi.WatchInfo

object KeepAliveManager {

    fun start(context: Context) {
        val immediateWorkRequest = OneTimeWorkRequestBuilder<LongRunningWorker>().build()
        WorkManager.getInstance(context).enqueue(immediateWorkRequest)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("LongRunningWorker")

        fun cancelAllNotifications(context: Context) {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancelAll()
        }

        cancelAllNotifications(context)
    }

    class LongRunningWorker(appContext: Context, workerParams: WorkerParameters) :
        Worker(appContext, workerParams) {

        override fun doWork(): Result {
            showNotification("G-Shock Smart Sync", "Running...")
            return Result.success()
        }

        @SuppressLint("MissingPermission")
        private fun showNotification(title: String, message: String) {
            val channelId = "gshock_smart_sync_keep_alive_channel"

            val channel = NotificationChannel(
                channelId,
                "G-Shock Keep Alive Notification",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(PRIORITY_DEFAULT)
                .setOngoing(true)

            with(NotificationManagerCompat.from(applicationContext)) {
                notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
            }
        }
    }
}