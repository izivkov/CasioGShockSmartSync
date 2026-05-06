package org.avmedia.gshockGoogleSync.ui.actions

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.Madhab
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime

class PrayerVerificationTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun verifySrinagarPrayerTimes() {
        // Markazi Jama Masjid Srinagar coordinates
        val latitude = 34.0938
        val longitude = 74.8152
        val coordinates = Coordinates(latitude, longitude)

        // Date: March 24, 2026
        val date = DateComponents(2026, 3, 24)

        // Settings from PrayerAlarmsHelper for Jammu & Kashmir
        val method = CalculationMethod.KARACHI
        val adjustments = PrayerAdjustments()
        val madhab = Madhab.HANAFI

        val parameters = method.parameters.copy(
            prayerAdjustments = adjustments,
            madhab = madhab
        )

        val prayerTimes = PrayerTimes(coordinates, date, parameters)

        val zoneId = ZoneId.of("Asia/Kolkata") // IST
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        fun formatTime(time: kotlin.time.Instant): String {
            val instant = Instant.ofEpochMilli(time.toEpochMilliseconds())
            return LocalDateTime.ofInstant(instant, zoneId).format(formatter)
        }

        println("Prayer Times for Srinagar on 2026-03-24 (Karachi/Hanafi):")
        println("Fajr: ${formatTime(prayerTimes.fajr)}")
        println("Sunrise: ${formatTime(prayerTimes.sunrise)}")
        println("Dhuhr: ${formatTime(prayerTimes.dhuhr)}")
        println("Asr: ${formatTime(prayerTimes.asr)}")
        println("Maghrib: ${formatTime(prayerTimes.maghrib)}")
        println("Isha: ${formatTime(prayerTimes.isha)}")
        
        // Also Sunset/Gurub (usually same as Maghrib if no adjustment)
        // Note: adhan2 might have a separate sunset field or it's maghrib
        // The image shows Gurub 6:47 and Magrib 6:48.
    }
}
