package org.avmedia.gShockPhoneSync.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import com.luckycatlabs.sunrisesunset.dto.Location as SunLocation

object NightWatcher {

    private var sunriseTime: LocalTime? = null
    private var sunsetTime: LocalTime? = null

    class SunriseSunsetWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
        override fun doWork(): Result {
            val isSunrise = inputData.getBoolean("isSunrise", true)
            if (isSunrise) {
                ProgressEvents.onNext("onSunrise")
            } else {
                ProgressEvents.onNext("onSunset")
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
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        sunriseTime = LocalTime.parse(sunrise, DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        sunsetTime = LocalTime.parse(sunset, DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
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
        val location = LocationProvider.getLocation(context)
        val latitude = location?.latitude
        val longitude = location?.longitude
        if (latitude == null || longitude == null) {
            Timber.i ("NightWatcher: cannot get location")
            return
        }

        val (sunrise, sunset) = calculateSunriseSunset(latitude, longitude)
        scheduleSunriseSunsetTasks(context, sunrise, sunset)
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
