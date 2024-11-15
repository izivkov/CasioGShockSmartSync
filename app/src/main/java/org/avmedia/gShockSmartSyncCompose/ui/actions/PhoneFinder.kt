package org.avmedia.gShockSmartSyncCompose.ui.actions

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PhoneFinder {
    companion object {

        private var mp: MediaPlayer? = null
        var resetVolume: () -> Unit? = {}

        fun ring(context: Context) {
            // get alarm uri
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                AppSnackbar("Unable to get default sound URI")
                return
            }

            // set volume to maximum
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            resetVolume = {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    previousVolume,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                AudioManager.FLAG_SHOW_UI
            )

            // init media player
            mp = MediaPlayer()
            mp!!.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
            mp!!.setDataSource(context, alarmUri)
            mp!!.prepare()
            mp!!.start()
            mp!!.isLooping = true

            detectPhoneLifting(context)
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

        private fun detectPhoneLifting(context: Context) {
            // use accelerometer sensor values to detect phone lifting
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometerSensor =
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

            class PhoneLiftEventListener : SensorEventListener {

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                }

                override fun onSensorChanged(event: SensorEvent?) {
                    val x = event!!.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    if (abs(x) > 1 || abs(y) > 1 || abs(z) > 1) {
                        sensorManager.unregisterListener(this)
                        RingCanceler.cancelAction()
                        stopRing()
                    }
                }
            }

            val phoneLiftListener = PhoneLiftEventListener()

            fun cancelRing() {
                sensorManager.unregisterListener(phoneLiftListener)
                stopRing()
            }

            // Stop ring after 30 seconds if phone not found.
            RingCanceler.callFunctionAfterDelay(30000, ::cancelRing)

            sensorManager.registerListener(
                phoneLiftListener,
                accelerometerSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
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
}