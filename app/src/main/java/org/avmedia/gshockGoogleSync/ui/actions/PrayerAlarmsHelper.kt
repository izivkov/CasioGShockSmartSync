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
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockGoogleSync.scratchpad.ScratchpadManager
import org.avmedia.gshockapi.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlinx.datetime.Instant as AdhanInstant

@Singleton
class PrayerAlarmsHelper @Inject constructor(
    private val alarmNameStorage: AlarmNameStorage,
    private val scratchpadManager: ScratchpadManager,
    @ApplicationContext private val context: Context
) {

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
     * @param n The number of upcoming prayer alarms to generate. Must be between 1 and 5.
     * @return A `Result<List<Alarm>>` which contains the list of `Alarm` objects on success,
     *         or an exception on failure (e.g., if location could not be determined).
     */
    @OptIn(ExperimentalTime::class)
    fun createNextPrayerAlarms(
        n: Int
    ): Result<List<Alarm>> = runCatching {
        // First, validate that the requested number of alarms is within a reasonable range.
        require(n in 1..5) { "Number of alarms must be between 1 and 5" }

        // Use the context injected into the class constructor
        val location = LocationProvider.getLocation(context) ?: throw IllegalStateException(
            "Could not obtain location"
        )
        val coordinates = Coordinates(location.latitude, location.longitude)
        val countryCode = LocationProvider.getCountryCode(context)
        val parameters = getCalculationMethodForLocation(countryCode).parameters
            .copy(prayerAdjustments = PrayerAdjustments())

        generateSequence(LocalDate.now()) { it.plusDays(1) }
            .map { date ->
                PrayerTimes(
                    coordinates,
                    DateComponents(date.year, date.monthValue, date.dayOfMonth),
                    parameters
                )
            }
            .flatMap { prayerTimes ->
                sequenceOf(
                    "Fajr" to prayerTimes.fajr,
                    "Dhuhr" to prayerTimes.dhuhr,
                    "Asr" to prayerTimes.asr,
                    "Maghrib" to prayerTimes.maghrib,
                    "Isha" to prayerTimes.isha
                )
            }
            .filter { (_, prayerTime) ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(prayerTime.toEpochMilliseconds()),
                    ZoneId.systemDefault()
                ) > LocalDateTime.now()
            }
            .map { (name, prayerTime) ->
                prayerTimeToAlarm(prayerTime, name)
            }
            .take(n)
            .toList()
            .also { alarms ->
                kotlinx.coroutines.runBlocking {
                    alarms.forEachIndexed { index, alarm ->
                        alarmNameStorage.put(alarm.name ?: "", index = index)
                    }
                    scratchpadManager.save()
                }
            }
    }.onFailure { e ->
        AppSnackbar("Failed to create next prayer alarms: ${e.message}")
    }

    /**
     * Determines the appropriate prayer time calculation method based on a given country code.
     *
     * @param countryCode The two-letter ISO country code (e.g., "US", "TR", "IN").
     * @return The best `CalculationMethod` for that country.
     */
    private fun getCalculationMethodForLocation(countryCode: String?): CalculationMethod =
        when {
            // Prioritize the Turkish method for Turkey and the wider European continent.
            isInTurkeyOrEurope(countryCode) -> CalculationMethod.TURKEY

            // Then, handle other specific regions.
            else -> when (countryCode) {
                "US", "CA" -> CalculationMethod.NORTH_AMERICA
                "EG" -> CalculationMethod.EGYPTIAN
                "PK", "IN", "BD" -> CalculationMethod.KARACHI
                "SA" -> CalculationMethod.UMM_AL_QURA
                "AE" -> CalculationMethod.DUBAI
                "QA" -> CalculationMethod.QATAR
                "KW" -> CalculationMethod.KUWAIT
                "SG" -> CalculationMethod.SINGAPORE
                // Global default for all other regions or if countryCode is null
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
        }

    private fun isInTurkeyOrEurope(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return isTurkey(countryCode) || isEurope(countryCode)
    }

    private fun isTurkey(countryCode: String): Boolean =
        countryCode.uppercase(Locale.US) == "TR"

    private fun isEurope(countryCode: String?): Boolean =
        setOf(
            "AL", "AD", "AM", "AT", "AZ", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE",
            "FI", "FR", "GE", "DE", "GR", "HU", "IS", "IE", "IT", "KZ", "XK", "LV", "LI", "LT",
            "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS",
            "SK", "SI", "ES", "SE", "CH", "UA", "GB", "VA"
        ).contains(countryCode?.uppercase(Locale.US))

    @OptIn(ExperimentalTime::class)
    private fun prayerTimeToAlarm(prayerTime: AdhanInstant, name: String): Alarm =
        getHoursAndMinutesFromEpochMilliseconds(prayerTime.toEpochMilliseconds()).let { (hours, minutes) ->
            val alarm = Alarm(hours, minutes, enabled = true, hasHourlyChime = false, name = name)
            alarm
        }

    private fun getHoursAndMinutesFromEpochMilliseconds(epochMilliseconds: Long): Pair<Int, Int> =
        Instant.ofEpochMilli(epochMilliseconds)
            .let { instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) }
            .let { localDateTime -> localDateTime.hour to localDateTime.minute }
}
