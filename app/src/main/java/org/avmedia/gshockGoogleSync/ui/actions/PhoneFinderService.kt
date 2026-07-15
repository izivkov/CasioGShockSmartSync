package org.avmedia.gshockGoogleSync.ui.actions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PhoneFinderService : Service(), SensorEventListener {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var startTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "phone_finder_service_channel"
        private const val WAKE_LOCK_TAG = "gshockGoogleSync:PhoneFinderServiceWakeLock"

        private val _serviceStopped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val serviceStopped: SharedFlow<Unit> = _serviceStopped.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, PhoneFinderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PhoneFinderService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("PhoneFinderService created")
        acquireWakeLock()
        setupSensor()
        setupDisconnectListener()
    }

    private fun setupSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun setupDisconnectListener() {
        val eventActions = arrayOf(
            EventAction("Disconnect") {
                Timber.d("PhoneFinderService: Watch disconnected, stopping")
                stopSelf()
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode() + "PhoneFinderService", eventActions)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("PhoneFinderService started")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }

        val alarmUri = getAlarmUri(this)
        if (alarmUri != null) {
            startAudio(alarmUri)
            startListening()
        } else {
            Timber.e("No alarm URI found, stopping service")
            stopSelf()
        }

        // Auto-stop after 30 seconds
        Executors.newSingleThreadScheduledExecutor().schedule({
            Timber.d("PhoneFinderService: 30s timeout reached, stopping")
            stopSelf()
        }, 30, TimeUnit.SECONDS)

        return START_NOT_STICKY
    }

    private fun startListening() {
        startTime = System.currentTimeMillis()
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (System.currentTimeMillis() - startTime < 2000) return

        event?.takeIf { it.sensor.type == Sensor.TYPE_ACCELEROMETER }?.let { e ->
            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]

            val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
            val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)

            if (deviation > 3.0f) {
                Timber.d("Phone picked up: deviation=$deviation")
                stopListening()
                stopSelf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("PhoneFinderService destroyed")
        stopAudio()
        stopListening()
        releaseWakeLock()
        _serviceStopped.tryEmit(Unit)
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(40_000L)
        }
        Timber.d("PhoneFinderService: WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("PhoneFinderService: WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createNotification(): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phone Finder Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Phone Finder audible alarm"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, FindPhoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phone Finder Active")
            .setContentText("Your phone is ringing!")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()
    }

    private fun startAudio(alarmUri: android.net.Uri) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            .setOnAudioFocusChangeListener { }
            .build()

        audioFocusRequest = focusRequest
        val focusResult = audioManager.requestAudioFocus(focusRequest)
        Timber.d("PhoneFinderService: Audio focus result: $focusResult")

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(this@PhoneFinderService, alarmUri)
            isLooping = true
            setWakeMode(this@PhoneFinderService, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
            start()
        }
        Timber.d("PhoneFinderService: MediaPlayer started")
    }

    private fun stopAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        audioFocusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
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
}