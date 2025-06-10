package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object PhoneFinder {
    private data class State(
        val mediaPlayer: MediaPlayer? = null,
        val sensorHandler: SensorHandler? = null,
        val resetVolume: () -> Unit = {}
    )

    private var state = State()

    fun ring(context: Context): Result<Unit> = runCatching {
        getAlarmUri()?.let { uri ->
            handleAudio(context, uri)
            detectPhoneLifting(context)
        } ?: throw IllegalStateException("No alarm URI available")
    }.onFailure { e ->
        Timber.e(e, "Failed to ring phone")
        AppSnackbar(context.getString(R.string.unable_to_get_default_sound_uri))
    }

    private fun getAlarmUri() =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun handleAudio(context: Context, alarmUri: android.net.Uri) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        // Set maximum volume
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            AudioManager.FLAG_PLAY_SOUND
        )

        // Update state with new MediaPlayer and reset volume function
        state = state.copy(
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(context, alarmUri)
                prepare()
                isLooping = true
                start()
            },
            resetVolume = {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    previousVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
            }
        )
    }

    private fun detectPhoneLifting(context: Context) {
        state = state.copy(sensorHandler = SensorHandler(context) { stopRing() })
        state.sensorHandler?.startListening(context)

        RingCanceler.schedule(30000) {
            state.sensorHandler?.stopListening()
            stopRing()
        }
    }

    private fun stopRing() {
        state.mediaPlayer?.let { player ->
            player.stop()
            player.release()
        }
        state.resetVolume()
        state = State() // Reset state
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
}

private class SensorHandler(
    context: Context,
    private val onPhoneLifted: () -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = getBestAvailableSensor(sensorManager)

    fun startListening(context: Context) {
        val sensorDelay =
            if (context.checkSelfPermission(android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                SensorManager.SENSOR_DELAY_FASTEST
            } else {
                SensorManager.SENSOR_DELAY_GAME
            }

        accelerometer?.let { sensor ->
            sensorManager.registerListener(this, sensor, sensorDelay)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
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

    override fun onSensorChanged(event: SensorEvent?) {
        event?.takeIf { it.sensor.type == Sensor.TYPE_ACCELEROMETER }?.values?.let { values ->
            if (values.size >= 3 && values.any { abs(it) > 11 }) {
                Timber.d("Phone lifted: x=${values[0]}, y=${values[1]}, z=${values[2]}")
                stopListening()
                RingCanceler.cancel()
                onPhoneLifted()
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Needed to satisfy interface.
    }

    private fun getBestAvailableSensor(sensorManager: SensorManager): Sensor? =
        listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GRAVITY
        ).firstNotNullOfOrNull { sensorManager.getDefaultSensor(it) }
}
