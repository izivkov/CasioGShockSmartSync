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
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PhoneFinder(context: Context) {
    companion object {
        private var mp: MediaPlayer? = null
        var resetVolume: () -> Unit? = {}
        private var phoneFinder: PhoneFinder? = null

        fun ring(context: Context) {
            // get alarm uri
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                AppSnackbar(
                    context.getString(
                        R.string.unable_to_get_default_sound_uri
                    )
                )
            }

            // set volume to maximum
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            resetVolume = {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    previousVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
            }
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                AudioManager.FLAG_PLAY_SOUND
            )

            // init media player
            mp = MediaPlayer()
            mp!!.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            mp!!.setDataSource(context, alarmUri)
            mp!!.prepare()
            mp!!.isLooping = true
            mp!!.start()

            detectPhoneLifting(context)
        }

        private fun detectPhoneLifting(context: Context) {
            phoneFinder = PhoneFinder(context)
            phoneFinder?.startListening(context)

            // Stop ring after 30 seconds if phone not found
            RingCanceler.callFunctionAfterDelay(30000) {
                phoneFinder?.stopListening()
                stopRing()
            }
        }

        private fun stopRing() {
            Timber.i("Stopping ring...")
            if (mp != null) {
                mp!!.stop()
                mp!!.release()
                mp = null
            }
            resetVolume()
        }

        object RingCanceler {
            private var executor: ScheduledExecutorService? = null

            fun callFunctionAfterDelay(
                delay: Long,
                action: () -> Unit
            ) {
                executor = Executors.newSingleThreadScheduledExecutor()
                executor?.schedule(action, delay, TimeUnit.MILLISECONDS)
            }

            fun cancelAction() {
                executor?.shutdownNow()
            }
        }
    }

    private inner class SensorHandler(context: Context) : SensorEventListener {
        private val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = getBestAvailableSensor(sensorManager)
        private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        private var lastZ: Float = 0f
        private val motionThreshold = 2.5f

        fun startListening(context: Context) {
            val sensorDelay =
                if (context.checkSelfPermission(android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    SensorManager.SENSOR_DELAY_FASTEST
                } else {
                    SensorManager.SENSOR_DELAY_GAME
                }

            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, accelerometer, sensorDelay)
        }

        fun stopListening() {
            sensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    handleAccelerometerEvent(it.values)
                }
            }
        }

        private fun handleAccelerometerEvent(values: FloatArray) {
            if (values.size < 3) {
                Timber.d("Unexpected sensor data size: ${values.size}, values: ${values.contentToString()}")
                return
            }

            val x = values[0]
            val y = values[1]
            val z = values[2]

            // Check for significant motion in any axis
            if (abs(x) > 11 || abs(y) > 11 || abs(z) > 11) {
                Timber.d("Phone lifted: x=$x, y=$y, z=$z")
                stopListening()
                RingCanceler.cancelAction()
                stopRing()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not used
        }

        private fun getBestAvailableSensor(sensorManager: SensorManager): Sensor? {
            return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        }
    }

    private var sensorHandler: SensorHandler? = null

    init {
        sensorHandler = SensorHandler(context)
    }

    fun startListening(context: Context) {
        sensorHandler?.startListening(context)
    }

    fun stopListening() {
        sensorHandler?.stopListening()
    }
}