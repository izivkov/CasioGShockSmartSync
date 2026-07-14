package org.avmedia.gshockGoogleSync.ui.time

import android.content.Context
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.data.DateComponents
import com.batoulapps.adhan2.internal.SolarTime
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.services.LocationProvider
import java.util.Calendar
import java.util.TimeZone

object SolarTimeHelper {

    fun getLocalMeanTimeOffsetMs(longitude: Double): Long {
        val zoneOffsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
        val longitudeOffsetMs = (longitude * (3600000.0 / 15.0)).toLong()
        return longitudeOffsetMs - zoneOffsetMs
    }

    fun getLocalSolarTimeOffsetMs(latitude: Double, longitude: Double): Long {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val dateComponents = DateComponents(
            now.get(Calendar.YEAR),
            (now.get(Calendar.MONTH) + 1),
            now.get(Calendar.DAY_OF_MONTH),
        )
        val coordinates = Coordinates(latitude, longitude)
        val solarTime = SolarTime(dateComponents, coordinates)

        val transitUtcHours = solarTime.transit
        val zoneOffsetHours = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3600000.0

        val offsetHours = 12.0 - transitUtcHours - zoneOffsetHours
        return (offsetHours * 3600000.0).toLong()
    }

    private fun meanSiderealTime(T: Double): Double {
        val JD = T * 36525 + 2451545.0
        val term1 = 280.46061837
        val term2 = 360.98564736629 * (JD - 2451545.0)
        val term3 = 0.000387933 * Math.pow(T, 2.0)
        val term4 = Math.pow(T, 3.0) / 38710000.0
        val theta = term1 + term2 + term3 - term4
        var result = theta % 360.0
        if (result < 0) {
            result += 360.0
        }
        return result
    }

    fun getSiderealTimeOffsetMs(longitude: Double): Long {
        val nowMs = System.currentTimeMillis()
        val JD = nowMs / 86400000.0 + 2440587.5
        val T = (JD - 2451545.0) / 36525.0
        val gmstDegrees = meanSiderealTime(T)
        var lmstDegrees = (gmstDegrees + longitude) % 360.0
        if (lmstDegrees < 0) {
            lmstDegrees += 360.0
        }
        val lmstHours = lmstDegrees / 15.0

        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)
        val ms = calendar.get(Calendar.MILLISECOND)
        val systemTimeOfDayMs = hours * 3600000L + minutes * 60000L + seconds * 1000L + ms

        val lmstTimeOfDayMs = (lmstHours * 3600000.0).toLong()

        return lmstTimeOfDayMs - systemTimeOfDayMs
    }

    fun calculateTimeOffset(context: Context, option: TimeSettingsStorage.TimeZoneOption): Long {
        val location = LocationProvider.getLocation(context) ?: return 0L
        return when (option) {
            TimeSettingsStorage.TimeZoneOption.SYSTEM -> 0L
            TimeSettingsStorage.TimeZoneOption.LOCAL_MEAN_TIME -> getLocalMeanTimeOffsetMs(
                location.longitude
            )
            TimeSettingsStorage.TimeZoneOption.LOCAL_SOLAR_TIME -> getLocalSolarTimeOffsetMs(
                location.latitude,
                location.longitude
            )
            TimeSettingsStorage.TimeZoneOption.SIDEREAL_TIME -> getSiderealTimeOffsetMs(
                location.longitude
            )
        }
    }
}