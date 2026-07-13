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
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockGoogleSync.utils.Utils
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
        getAlarmUri(context)?.let { uri ->
            handleAudio(context, uri)
            detectPhoneLifting(context)
        } ?: throw IllegalStateException("No playable alarm or ringtone available on this device")
    }.onFailure { e ->
        Timber.e(e, "Failed to ring phone: ${e.message}")
        AppSnackbar("Unable to play alarm sound: ${e.message}")
    }

    private fun getAlarmUri(context: Context): android.net.Uri? {
        val candidates = listOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).filterNotNull()

        // Try the standard "default" indirection URIs first.
        for (uri in candidates) {
            if (isPlayable(context, uri)) return uri
        }

        // None of the defaults are playable (e.g. unset on this device/emulator) —
        // fall back to the first concrete ringtone the system actually has on file.
        val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
        val cursor = manager.cursor
        if (cursor.moveToFirst()) {
            return manager.getRingtoneUri(cursor.position)
        }

        return null
    }

    private fun isPlayable(context: Context, uri: android.net.Uri): Boolean {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun handleAudio(context: Context, alarmUri: android.net.Uri) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            AudioManager.FLAG_PLAY_SOUND
        )

        val resetVolume: () -> Unit = {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, AudioManager.FLAG_PLAY_SOUND)
        }

        val player = createPlayer(context, alarmUri)
        if (player == null) {
            resetVolume()
            throw IllegalStateException("Unable to create a playable MediaPlayer for URI: $alarmUri")
        }

        state = state.copy(mediaPlayer = player, resetVolume = resetVolume)
    }

    private fun createPlayer(context: Context, uri: android.net.Uri): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.prepare()
            player.isLooping = true
            player.start()
            player
        } catch (e: Exception) {
            Timber.e(e, "Failed to create MediaPlayer for URI: $uri")
            player.release() // clean up immediately instead of leaking until GC finalizes it
            null
        }
    }

    private fun detectPhoneLifting(context: Context) {
        state = state.copy(sensorHandler = SensorHandler(context) { stopRing() })
        state.sensorHandler?.startListening(context)

        RingCanceler.schedule(30000) {
            state.sensorHandler?.stopListening()
            stopRing()
        }

        setupDisconnectListener()
    }

    private fun setupDisconnectListener() {
        val eventActions = arrayOf(
            EventAction("Disconnect") {
                stopRing()
            }
        )
        ProgressEvents.runEventActions(Utils.AppHashCode() + "PhoneFinder", eventActions)
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

    private class SensorHandler(
        context: Context,
        private val onPhoneLifted: () -> Unit
    ) : SensorEventListener {
        private val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val pickUpSensor = sensorManager.getDefaultSensor(25) // Hidden TYPE_PICK_UP_GESTURE
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        private val triggerEventListener = object : android.hardware.TriggerEventListener() {
            override fun onTrigger(event: android.hardware.TriggerEvent?) {
                Timber.d("Pick-up gesture triggered")
                stopListening()
                onPhoneLifted()
            }
        }

        fun startListening(context: Context) {
            if (pickUpSensor != null) {
                Timber.d("Using TYPE_PICK_UP_GESTURE")
                sensorManager.requestTriggerSensor(triggerEventListener, pickUpSensor)
                return
            }

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
            if (pickUpSensor != null) {
                sensorManager.cancelTriggerSensor(triggerEventListener, pickUpSensor)
            }
            sensorManager.unregisterListener(this)
        }

        // --- Gesture-based pickup detection (works even while walking) ---

        // We track the gravity-component direction (low-passed accel vector) to detect
        // a genuine reorientation, plus a magnitude spike to detect the grab itself.

        private var gravX = 0f
        private var gravY = 0f
        private var gravZ = SensorManager.GRAVITY_EARTH
        private var gravityInitialized = false
        private val gravityAlpha = 0.8f  // low-pass filter constant

        private var baselineGravX = 0f
        private var baselineGravY = 0f
        private var baselineGravZ = SensorManager.GRAVITY_EARTH
        private var lastBaselineUpdateMs = 0L
        private val baselineUpdateIntervalMs = 1500L // refresh "resting orientation" periodically

        private var lastTriggerTimeMs = 0L
        private val cooldownMs = 2000L

        // Spike threshold: a deliberate grab is sharper than a footstep bounce.
        private val grabSpikeThreshold = 4.0f

        // Orientation change threshold: angle (in terms of dot product) between
        // baseline gravity direction and current gravity direction.
        private val reorientationDotThreshold = 0.85f // cos(~32°) — below this = reoriented

        override fun onSensorChanged(event: SensorEvent?) {
            event?.takeIf { it.sensor.type == Sensor.TYPE_ACCELEROMETER }?.let { e ->
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]

                val now = System.currentTimeMillis()
                val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                val deviation = abs(magnitude - SensorManager.GRAVITY_EARTH)

                // Low-pass filter to isolate the gravity component (current orientation)
                if (!gravityInitialized) {
                    gravX = x; gravY = y; gravZ = z
                    baselineGravX = x; baselineGravY = y; baselineGravZ = z
                    lastBaselineUpdateMs = now
                    gravityInitialized = true
                    return
                }
                gravX = gravityAlpha * gravX + (1 - gravityAlpha) * x
                gravY = gravityAlpha * gravY + (1 - gravityAlpha) * y
                gravZ = gravityAlpha * gravZ + (1 - gravityAlpha) * z

                // Periodically refresh the "resting orientation" baseline, but ONLY when
                // motion is calm — so walking's steady bounce doesn't get treated as a
                // pickup, and so the baseline tracks "phone sitting in pocket" rather
                // than a moment mid-grab.
                if (deviation < 1.0f && now - lastBaselineUpdateMs > baselineUpdateIntervalMs) {
                    baselineGravX = gravX; baselineGravY = gravY; baselineGravZ = gravZ
                    lastBaselineUpdateMs = now
                }

                // Detect a grab: sharp spike in magnitude (the act of snatching the phone)
                if (deviation > grabSpikeThreshold && now - lastTriggerTimeMs > cooldownMs) {
                    // Confirm with an orientation check: has the gravity direction
                    // shifted meaningfully from the established baseline? Walking
                    // jostles but does not reorient the phone in a pocket; pulling
                    // it out does.
                    val dot = normalizedDot(gravX, gravY, gravZ, baselineGravX, baselineGravY, baselineGravZ)

                    if (dot < reorientationDotThreshold) {
                        Timber.d("Phone picked up: deviation=$deviation, orientationDot=$dot")
                        lastTriggerTimeMs = now
                        stopListening()
                        onPhoneLifted()
                    } else {
                        Timber.d("Spike without reorientation (likely footstep): deviation=$deviation, dot=$dot")
                    }
                }
            }
        }

        private fun normalizedDot(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float
        ): Float {
            val magA = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
            val magB = kotlin.math.sqrt(bx * bx + by * by + bz * bz)
            if (magA == 0f || magB == 0f) return 1f
            return (ax * bx + ay * by + az * bz) / (magA * magB)
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            // Needed to satisfy interface.
        }
    }
}
