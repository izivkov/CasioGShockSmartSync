/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.time.ExperimentalTime

object PrayerAlarmsHelper {

    /**
     * Calculates and returns the next `n` prayer times that occur after the current moment,
     * converting them into a list of `Alarm` objects.
     *
     * This function constructs a lazy, infinite sequence of dates starting from today. For each date,
     * it calculates the prayer times, flattens them into a single continuous stream, filters out
     * any times that have already passed, converts the future ones to `Alarm` objects,
     * and finally takes the first `n` results. This approach is highly efficient as it
     * avoids calculating unnecessary data.
     *
     * @param context The application context, used to retrieve the device's current location.
     * @param n The number of upcoming prayer alarms to generate. Must be between 1 and 5.
     * @return A `Result<List<Alarm>>` which contains the list of `Alarm` objects on success,
     *         or an exception on failure (e.g., if location could not be determined).
     */
    @OptIn(ExperimentalTime::class)
    fun createNextPrayerAlarms(context: Context, n: Int): Result<List<Alarm>> = runCatching {
        // First, validate that the requested number of alarms is within a reasonable range.
        require(n in 1..5) { "Number of alarms must be between 1 and 5" }

        // Get the device's current location. If unavailable, throw an exception to be caught by runCatching.
        val location = LocationProvider.getLocation(context) ?: throw IllegalStateException(
            "Could not obtain location"
        )

        // Prepare the necessary parameters for the Adhan2 library.
        val coordinates = Coordinates(location.latitude, location.longitude)
        val parameters = getCalculationMethodForLocation().parameters
            .copy(prayerAdjustments = PrayerAdjustments())

        // Start a lazy, infinite sequence of dates, beginning with today.
        // "Lazy" means it only computes the next date when asked, saving resources.
        generateSequence(LocalDate.now()) { it.plusDays(1) }
            // For each date in the sequence, calculate the corresponding prayer times for that day.
            // This transforms the stream from Dates to PrayerTimes objects.
            .map { date ->
                PrayerTimes(
                    coordinates,
                    DateComponents(date.year, date.monthValue, date.dayOfMonth),
                    parameters
                )
            }
            // Take each PrayerTimes object (containing 5 prayer times) and flatten it.
            // This converts a stream of daily-grouped prayers into one continuous stream of individual prayers.
            // E.g., [[fajr1, dhuhr1], [fajr2, dhuhr2]] -> [fajr1, dhuhr1, fajr2, dhuhr2]
            .flatMap { prayerTimes ->
                sequenceOf(
                    prayerTimes.fajr,
                    prayerTimes.dhuhr,
                    prayerTimes.asr,
                    prayerTimes.maghrib,
                    prayerTimes.isha
                )
            }
            // Filter the continuous stream, keeping only the prayer times that are in the future.
            .filter { prayerTime ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(prayerTime.toEpochMilliseconds()),
                    ZoneId.systemDefault()
                ) > LocalDateTime.now()
            }
            // Convert each future prayer time from the Adhan library's Instant format into our app's `Alarm` object.
            .map(::prayerTimeToAlarm)
            // Take only the first `n` items from the resulting stream. This is efficient because
            // it stops the entire sequence pipeline as soon as the desired number is reached.
            .take(n)
            // Convert the final sequence of `n` alarms into a List. This is the successful result.
            .toList()
    }.onFailure { e ->
        AppSnackbar("Failed to create next prayer alarms: ${e.message}")
    }
    private fun getCalculationMethodForLocation(): CalculationMethod =
        when {
            isInTurkeyOrEurope() -> CalculationMethod.TURKEY
            else -> when (Locale.getDefault().country.uppercase(Locale.US)) {
                "US", "CA" -> CalculationMethod.NORTH_AMERICA
                "EG" -> CalculationMethod.EGYPTIAN
                "PK", "IN", "BD" -> CalculationMethod.KARACHI
                "SA" -> CalculationMethod.UMM_AL_QURA
                "AE" -> CalculationMethod.DUBAI
                "QA" -> CalculationMethod.QATAR
                "KW" -> CalculationMethod.KUWAIT
                "SG" -> CalculationMethod.SINGAPORE
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
        }

    private fun isInTurkeyOrEurope(): Boolean =
        Locale.getDefault().country.let { country ->
            isTurkey(country) || isEurope(country)
        }

    private fun isTurkey(countryCode: String): Boolean =
        countryCode.uppercase() == "TR"

    private fun isEurope(countryCode: String): Boolean =
        setOf(
            "AL", "AD", "AM", "AT", "AZ", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE",
            "FI", "FR", "GE", "DE", "GR", "HU", "IS", "IE", "IT", "KZ", "XK", "LV", "LI", "LT",
            "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS",
            "SK", "SI", "ES", "SE", "CH", "UA", "GB", "VA"
        ).contains(countryCode.uppercase())

    @OptIn(ExperimentalTime::class)
    private fun prayerTimeToAlarm(prayerTime: kotlinx.datetime.Instant): Alarm =
        getHoursAndMinutesFromEpochMilliseconds(prayerTime.toEpochMilliseconds()).let { (hours, minutes) ->
            Alarm(hours, minutes, enabled = true, hasHourlyChime = false)
        }

    private fun getHoursAndMinutesFromEpochMilliseconds(epochMilliseconds: Long): Pair<Int, Int> =
        Instant.ofEpochMilli(epochMilliseconds)
            .let { instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) }
            .let { localDateTime -> localDateTime.hour to localDateTime.minute }
}
