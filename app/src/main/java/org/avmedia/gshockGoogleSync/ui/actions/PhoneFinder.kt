package org.avmedia.gshockGoogleSync.ui.actions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import timber.log.Timber
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockGoogleSync.utils.Utils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object PhoneFinder {
    private const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "phone_finder_channel"
    private const val WAKE_LOCK_TAG = "gshockGoogleSync:PhoneFinderWakeLock"
    private const val WAKE_LOCK_TIMEOUT_MS = 35_000L

    private data class State(
        val mediaPlayer: MediaPlayer? = null,
        val sensorHandler: SensorHandler? = null,
        val wakeLock: PowerManager.WakeLock? = null,
        val audioFocusRequest: AudioFocusRequest? = null,
        val resetVolume: () -> Unit = {}
    )

    private var state = State()

    fun ring(context: Context): Result<Unit> = runCatching {
        Timber.d("PhoneFinder: ring() called")
        acquireWakeLock(context)
        getAlarmUri(context)?.let { uri ->
            startAudio(context, uri)
            showNotification(context)
            detectPhoneLifting(context)
        } ?: throw IllegalStateException("No playable alarm or ringtone available")
    }.onFailure { e ->
        Timber.e(e, "Failed to ring phone")
        AppSnackbar(context.getString(R.string.unable_to_get_default_sound_uri))
        stopRing(context, "Ring failure")
    }

    private fun acquireWakeLock(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
            PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        Timber.d("PhoneFinder: wake lock acquired (Screen Bright + Wakeup)")
        state = state.copy(wakeLock = wakeLock)
    }

    private fun startAudio(context: Context, alarmUri: android.net.Uri) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            AudioManager.FLAG_PLAY_SOUND
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { } // Required for delayed focus gain
            .build()

        val focusResult = audioManager.requestAudioFocus(focusRequest)
        Timber.d("PhoneFinder: Audio focus request result: $focusResult")

        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(context, alarmUri)
            isLooping = true
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
            start()
        }

        val resetVolume: () -> Unit = {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

        state = state.copy(
            mediaPlayer = mediaPlayer, 
            audioFocusRequest = focusRequest, 
            resetVolume = resetVolume
        )
        Timber.d("PhoneFinder: MediaPlayer started. isPlaying=${mediaPlayer.isPlaying}")
    }

    private fun getAlarmUri(context: Context): android.net.Uri? {
        val candidates = listOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).filterNotNull()

        for (uri in candidates) {
            if (isPlayable(context, uri)) return uri
        }

        // Fallback: search for any available ringtone
        return try {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = manager.cursor
            if (cursor.moveToFirst()) {
                manager.getRingtoneUri(cursor.position)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isPlayable(context: Context, uri: android.net.Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Phone Finder",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for Phone Finder alarm"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Phone Finder Active")
            .setContentText("Your phone is ringing!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun detectPhoneLifting(context: Context) {
        state = state.copy(sensorHandler = SensorHandler(context) { stopRing(context, "Phone lifted") })
        state.sensorHandler?.startListening()

        // Auto-stop ringing after 30 seconds
        RingCanceler.schedule(30000) {
            stopRing(context, "30s timeout reached")
        }

        setupDisconnectListener(context)
    }

    private fun setupDisconnectListener(context: Context) {
        val eventActions = arrayOf(
            EventAction("Disconnect") {
                stopRing(context, "Watch disconnected")
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode() + "PhoneFinder", eventActions)
    }

    private fun stopRing(context: Context, reason: String = "Unknown") {
        Timber.d("PhoneFinder: stopRing() called. Reason: $reason")
        state.mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                Timber.e(e, "Error stopping MediaPlayer")
            }
        }
        state.resetVolume()
        state.sensorHandler?.stopListening()
        state.wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("PhoneFinder: wake lock released")
            }
        }
        
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        
        state = State() // Reset state
        Timber.d("PhoneFinder: Ringing stopped and state reset")
    }

    private object RingCanceler {
        private var executor: ScheduledExecutorService? = null

        fun schedule(delay: Long, action: () -> Unit) {
            executor?.shutdownNow()
            executor = Executors.newSingleThreadScheduledExecutor().apply {
                schedule(action, delay, TimeUnit.MILLISECONDS)
            }
        }

        fun cancel() {
            executor?.shutdownNow()
            executor = null
        }
    }

    private class SensorHandler(
        context: Context,
        private val onPhoneLifted: () -> Unit
    ) : SensorEventListener {
        private val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private var startTime = System.currentTimeMillis()

        fun startListening() {
            startTime = System.currentTimeMillis()
            accelerometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        fun stopListening() {
            sensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            // Ignore first 2 seconds to allow user to put phone down or avoid false triggers
            if (System.currentTimeMillis() - startTime < 2000) return

            event?.takeIf { it.sensor.type == Sensor.TYPE_ACCELEROMETER }?.let { e ->
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]

                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)

                // Increased threshold to 3.0f to avoid false triggers from vibration
                if (deviation > 3.0f) {
                    Timber.d("Phone picked up: deviation=$deviation")
                    stopListening()
                    onPhoneLifted()
                }
            }
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
    }
}
