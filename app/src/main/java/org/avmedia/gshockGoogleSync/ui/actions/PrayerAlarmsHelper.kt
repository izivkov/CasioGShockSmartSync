/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022-2026 . All rights reserved.
 */

package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockapi.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as AdhanInstant

@Singleton
class PrayerAlarmsHelper @Inject constructor(
    private val alarmNameStorage: AlarmNameStorage,
    @param:ApplicationContext private val context: Context
) {

    private companion object {
        const val SE_ASIA_BUFFER = 2

        // Jammu & Kashmir Geofence (approximate boundaries)
        const val JK_LAT_MIN = 32.0
        const val JK_LAT_MAX = 37.0
        const val JK_LNG_MIN = 73.0
        const val JK_LNG_MAX = 80.0
    }

    @OptIn(ExperimentalTime::class)
    suspend fun createNextPrayerAlarms(
        n: Int
    ): Result<List<Alarm>> = runCatching<List<Alarm>> {
        require(n in 1..5) { "Number of alarms must be between 1 and 5" }

        val location = LocationProvider.getLocation(context) ?: throw IllegalStateException(
            "Could not obtain location"
        )
        val coordinates = Coordinates(location.latitude, location.longitude)
        val countryCode = LocationProvider.getCountryCode(context)

        // Get the specific configuration for the region (method, adjustments, and school)
        val (method, adjustments, madhab) = getRegionalConfiguration(countryCode, coordinates)

        val parameters = method.parameters.copy(
            prayerAdjustments = adjustments,
            madhab = madhab
        )

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
                alarms.forEachIndexed { index, alarm ->
                    alarmNameStorage.put(alarm.name ?: "", index = index)
                }
                alarmNameStorage.save()
            }
    }.onFailure { e ->
        AppSnackbar("Failed to create next prayer alarms: ${e.message}")
    }

    /**
     * Determines regional settings.
     * Returns a Triple of (Method, Adjustments, Madhab).
     */
    private fun getRegionalConfiguration(
        countryCode: String?,
        co./rel ords: Coordinates
    ): Triple<CalculationMethod, PrayerAdjustments, Madhab> {
        val code = countryCode?.uppercase(Locale.US)

        return when {
            // Jammu & Kashmir Logic: Karachi Method + Hanafi Madhab
            code == "IN" && isJammuKashmirRegion(coords) -> {
                Triple(CalculationMethod.KARACHI, PrayerAdjustments(), Madhab.HANAFI)
            }

            // Indonesia: Singapore Method + 2m Buffer
            code == "ID" -> {
                Triple(CalculationMethod.SINGAPORE, getSeAsiaAdjustments(), Madhab.SHAFI)
            }

            // Malaysia: MWL Method + 2m Buffer
            code == "MY" -> {
                Triple(CalculationMethod.MUSLIM_WORLD_LEAGUE, getSeAsiaAdjustments(), Madhab.SHAFI)
            }

            // Brunei: Singapore Method + 2m Buffer
            code == "BN" -> {
                Triple(CalculationMethod.SINGAPORE, getSeAsiaAdjustments(), Madhab.SHAFI)
            }

            // Singapore: Explicitly Singapore Method, No Buffer
            code == "SG" -> {
                Triple(CalculationMethod.SINGAPORE, PrayerAdjustments(), Madhab.SHAFI)
            }

            // Default regional logic
            else -> {
                Triple(getCalculationMethodForLocation(code), PrayerAdjustments(), Madhab.SHAFI)
            }
        }
    }

    private fun isJammuKashmirRegion(coords: Coordinates): Boolean {
        return coords.latitude in JK_LAT_MIN..JK_LAT_MAX &&
                coords.longitude in JK_LNG_MIN..JK_LNG_MAX
    }

    private fun getSeAsiaAdjustments(): PrayerAdjustments = PrayerAdjustments(
        fajr = SE_ASIA_BUFFER,
        sunrise = -SE_ASIA_BUFFER,
        dhuhr = SE_ASIA_BUFFER,
        asr = SE_ASIA_BUFFER,
        maghrib = SE_ASIA_BUFFER,
        isha = SE_ASIA_BUFFER
    )

    private fun getCalculationMethodForLocation(countryCode: String?): CalculationMethod =
        when {
            isInTurkeyOrEurope(countryCode) -> CalculationMethod.TURKEY
            else -> when (countryCode) {
                "US", "CA" -> CalculationMethod.NORTH_AMERICA
                "EG" -> CalculationMethod.EGYPTIAN
                "PK", "IN", "BD" -> CalculationMethod.KARACHI
                "SA" -> CalculationMethod.UMM_AL_QURA
                "AE" -> CalculationMethod.DUBAI
                "QA" -> CalculationMethod.QATAR
                "KW" -> CalculationMethod.KUWAIT
                else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
        }

    private fun isInTurkeyOrEurope(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return isTurkey(countryCode) || isEurope(countryCode)
    }

    private fun isTurkey(countryCode: String): Boolean = countryCode == "TR"

    private fun isEurope(countryCode: String?): Boolean =
        setOf(
            "AL", "AD", "AM", "AT", "AZ", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE",
            "FI", "FR", "GE", "DE", "GR", "HU", "IS", "IE", "IT", "KZ", "XK", "LV", "LI", "LT",
            "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS",
            "SK", "SI", "ES", "SE", "CH", "UA", "GB", "VA"
        ).contains(countryCode)

    @OptIn(ExperimentalTime::class)
    private fun prayerTimeToAlarm(prayerTime: AdhanInstant, name: String): Alarm =
        getHoursAndMinutesFromEpochMilliseconds(prayerTime.toEpochMilliseconds()).let { (hours, minutes) ->
            Alarm(hours, minutes, enabled = true, hasHourlyChime = false, name = name)
        }

    private fun getHoursAndMinutesFromEpochMilliseconds(epochMilliseconds: Long): Pair<Int, Int> =
        Instant.ofEpochMilli(epochMilliseconds)
            .let { instant -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) }
            .let { localDateTime -> localDateTime.hour to localDateTime.minute }
}
