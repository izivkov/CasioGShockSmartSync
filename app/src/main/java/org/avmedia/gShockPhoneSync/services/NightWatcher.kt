package org.avmedia.gShockPhoneSync.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import kotlinx.coroutines.delay
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import com.luckycatlabs.sunrisesunset.dto.Location as SunLocation
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

object NightWatcher {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sunriseTime: LocalTime? = null
    private var sunsetTime: LocalTime? = null

    class SunriseSunsetWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
        override fun doWork(): Result {
            fun playChime() {
                val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500) // 500 ms duration
                toneGenerator.release()
            }

            val isSunrise = inputData.getBoolean("isSunrise", true)
            if (isSunrise) {
                ProgressEvents.onNext("onSunrise")
                playChime()
            } else {
                ProgressEvents.onNext("onSunset")
                playChime()
            }
            return Result.success()
        }
    }

    private fun calculateSunriseSunset(latitude: Double, longitude: Double): Pair<String, String> {
        val location = SunLocation(latitude, longitude)
        val calculator = SunriseSunsetCalculator(location, TimeZone.getDefault().id)
        val sunrise = calculator.getOfficialSunriseForDate(Calendar.getInstance())
        val sunset = calculator.getOfficialSunsetForDate(Calendar.getInstance())
        return Pair(sunrise, sunset)
    }

    private fun scheduleSunriseSunsetTasks(context: Context, sunrise: String, sunset: String) {
        val dateFormat = SimpleDateFormat("HH:mm"/*"hh:mm a"*/, Locale.getDefault())
        sunriseTime = LocalTime.parse(sunrise, DateTimeFormatter.ofPattern("HH:mm"/*"hh:mm a"*/, Locale.getDefault()))
        sunsetTime = LocalTime.parse(sunset, DateTimeFormatter.ofPattern("HH:mm"/*"hh:mm a"*/, Locale.getDefault()))
        val now = ZonedDateTime.now()

        val sunriseDelay = Duration.between(now.toLocalTime(), sunriseTime).toMillis()
        val sunsetDelay = Duration.between(now.toLocalTime(), sunsetTime).toMillis()

        val sunriseWorkRequest = OneTimeWorkRequestBuilder<SunriseSunsetWorker>()
            .setInitialDelay(sunriseDelay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("isSunrise" to true))
            .build()

        val sunsetWorkRequest = OneTimeWorkRequestBuilder<SunriseSunsetWorker>()
            .setInitialDelay(sunsetDelay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("isSunrise" to false))
            .build()

        WorkManager.getInstance(context).enqueue(sunriseWorkRequest)
        WorkManager.getInstance(context).enqueue(sunsetWorkRequest)
    }

    @SuppressLint("MissingPermission")
    fun setupSunriseSunsetTasks(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationTask: Task<Location> = fusedLocationClient.lastLocation
        locationTask.addOnSuccessListener { location ->
            location?.let {
                val latitude = it.latitude
                val longitude = it.longitude
                val (sunrise, sunset) = calculateSunriseSunset(latitude, longitude)
                scheduleSunriseSunsetTasks(context, sunrise, sunset)
            }
        }.addOnFailureListener { exception ->
            Timber.e("SunriseSunsetManager", "Error getting location: $exception")
        }
    }

    fun isNight(): Boolean {
        val now = LocalTime.now()
        return if (sunriseTime != null && sunsetTime != null) {
            now.isBefore(sunriseTime) || now.isAfter(sunsetTime)
        } else {
            false
        }
    }
}
