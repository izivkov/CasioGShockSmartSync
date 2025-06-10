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

object PrayerAlarmsHelper {
    fun createPrayerAlarms(context: Context): Result<List<Alarm>> = runCatching {
        val location = LocationProvider.getLocation(context) ?: throw IllegalStateException(
            "Could not obtain location"
        )

        val today = LocalDate.now()
        val date = DateComponents(today.year, today.monthValue, today.dayOfMonth)
        val coordinates = Coordinates(location.latitude, location.longitude)
        val parameters = getCalculationMethodForLocation().parameters
            .copy(prayerAdjustments = PrayerAdjustments(fajr = 2))

        PrayerTimes(coordinates, date, parameters).let { prayerTimes ->
            listOf(
                prayerTimes.fajr,
                prayerTimes.dhuhr,
                prayerTimes.asr,
                prayerTimes.maghrib,
                prayerTimes.isha
            ).map(::prayerTimeToAlarm)
        }
    }.onFailure { e ->
        AppSnackbar("Failed to create prayer alarms: ${e.message}")
    }

    fun createNextPrayerAlarms(context: Context, n: Int): Result<List<Alarm>> = runCatching {
        require(n in 1..5) { "Number of alarms must be between 1 and 5" }

        val location = LocationProvider.getLocation(context) ?: throw IllegalStateException(
            "Could not obtain location"
        )

        val coordinates = Coordinates(location.latitude, location.longitude)
        val parameters = getCalculationMethodForLocation().parameters
            .copy(prayerAdjustments = PrayerAdjustments(fajr = 2))

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
                    prayerTimes.fajr,
                    prayerTimes.dhuhr,
                    prayerTimes.asr,
                    prayerTimes.maghrib,
                    prayerTimes.isha
                )
            }
            .filter { prayerTime ->
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(prayerTime.toEpochMilliseconds()),
                    ZoneId.systemDefault()
                ) > LocalDateTime.now()
            }
            .map(::prayerTimeToAlarm)
            .take(n)
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

    private fun prayerTimeToAlarm(prayerTime: kotlinx.datetime.Instant): Alarm =
        getHoursAndMinutesFromEpochMilliseconds(prayerTime.toEpochMilliseconds()).let { (hours, minutes) ->
            Alarm(hours, minutes, enabled = true, hasHourlyChime = false)
        }

    private fun getHoursAndMinutesFromEpochMilliseconds(epochMilliseconds: Long): Pair<Int, Int> =
        Instant.ofEpochMilli(epochMilliseconds)
            .let { instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) }
            .let { localDateTime -> localDateTime.hour to localDateTime.minute }
}
