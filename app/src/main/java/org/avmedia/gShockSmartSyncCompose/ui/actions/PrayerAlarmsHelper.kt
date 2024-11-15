/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.actions

import android.content.Context
import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import org.avmedia.gShockSmartSyncCompose.services.LocationProvider
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import org.avmedia.gshockapi.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

object PrayerAlarmsHelper {

    fun createPrayerAlarms(context: Context): java.util.ArrayList<Alarm>? {

        val location = LocationProvider.getLocation(context)
        if (location == null) {
            AppSnackbar(
                "Could not obtain your location. Make sure FINE_LOCATION permission os granted."
            )
            return null
        }
        val today = LocalDate.now()
        val date = DateComponents(today.year, today.monthValue, today.dayOfMonth)

        val parameters = getCalculationMethodForLocation().parameters
            .copy(
                // madhab = Madhab.HANAFI,
                prayerAdjustments = PrayerAdjustments(fajr = 2)
            )

        val prayerTimes =
            PrayerTimes(Coordinates(location.latitude, location.longitude), date, parameters)

        val alarms = ArrayList<Alarm>()
        alarms.add(prayerTimeToAlarm(prayerTimes.fajr))
        alarms.add(prayerTimeToAlarm(prayerTimes.dhuhr))
        alarms.add(prayerTimeToAlarm(prayerTimes.asr))
        alarms.add(prayerTimeToAlarm(prayerTimes.maghrib))
        alarms.add(prayerTimeToAlarm(prayerTimes.isha))

        return alarms
    }

    private fun getCalculationMethodForLocation(): CalculationMethod {
        if (isInTurkeyOrEurope())
            return CalculationMethod.TURKEY

        return when (Locale.getDefault().country.uppercase(Locale.US)) {
            "US", "CA" -> CalculationMethod.NORTH_AMERICA
            "EG" -> CalculationMethod.EGYPTIAN
            "PK" -> CalculationMethod.KARACHI
            "SA" -> CalculationMethod.UMM_AL_QURA
            "AE" -> CalculationMethod.DUBAI
            "QA" -> CalculationMethod.QATAR
            "KW" -> CalculationMethod.KUWAIT
            "SG" -> CalculationMethod.SINGAPORE
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
    }

    private fun isInTurkeyOrEurope(): Boolean {
        val country = Locale.getDefault().country
        return isTurkey(country) || isEurope(country)
    }

    private fun isTurkey(countryCode: String): Boolean {
        return countryCode.equals("TR", ignoreCase = true)
    }

    private fun isEurope(countryCode: String): Boolean {
        val europeanCountries = setOf(
            "AL", "AD", "AM", "AT", "AZ", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE",
            "FI", "FR", "GE", "DE", "GR", "HU", "IS", "IE", "IT", "KZ", "XK", "LV", "LI", "LT",
            "LU", "MT", "MD", "MC", "ME", "NL", "MK", "NO", "PL", "PT", "RO", "RU", "SM", "RS",
            "SK", "SI", "ES", "SE", "CH", "UA", "GB", "VA"
        )
        return europeanCountries.contains(countryCode.uppercase())
    }

    private fun prayerTimeToAlarm(prayerTime: kotlinx.datetime.Instant): Alarm {
        val (hours, minutes) = getHoursAndMinutesFromEpochMilliseconds(prayerTime.toEpochMilliseconds())
        return Alarm(hours, minutes, enabled = true, hasHourlyChime = false)
    }

    private fun getHoursAndMinutesFromEpochMilliseconds(epochMilliseconds: Long): Pair<Int, Int> {
        val instant = Instant.ofEpochMilli(epochMilliseconds)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return localDateTime.hour to localDateTime.minute
    }
}