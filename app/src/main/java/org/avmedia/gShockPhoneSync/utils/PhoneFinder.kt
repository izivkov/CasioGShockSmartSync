package org.avmedia.gShockPhoneSync.utils

import org.avmedia.gShockPhoneSync.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import timber.log.Timber

class PhoneFinder : BroadcastReceiver() {
    companion object {
        val CHANNEL_ID = "G_SHOCK_PHONE_FINDER_CHANNEL"
        var mp: MediaPlayer? = null

        fun ring(context: Context) {
            // get alarm uri
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                Timber.e("ringAlarm", "Unable to get default sound URI")
                return
            }

            // set volume to maximum
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            while (
                audioManager.getStreamVolume(AudioManager.STREAM_ALARM) <
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            ) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_ALARM,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_PLAY_SOUND
                )
            }

            // init media player
            mp = MediaPlayer()
            mp!!.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            mp!!.setDataSource(context, alarmUri)
            mp!!.prepare()
            mp!!.start()
            mp!!.isLooping = true

            // create stop ringing notification
            val notificationManager: NotificationManager = context.applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "G-Shock Phone Finder",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            notificationManager.notify(
                0,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ring)
                    .setContentTitle("Phone finder")
                    .setContentText("Swipe to stop ringing")
                    .setDeleteIntent(
                        PendingIntent.getBroadcast(
                            context,
                            0,
                            Intent(context, PhoneFinder::class.java),
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .build()
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // stop ringing
        if (mp != null) {
            mp!!.stop()
            mp = null
        }
    }

}