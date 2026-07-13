package org.avmedia.gshockGoogleSync.utils

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.data.DateComponents
import com.batoulapps.adhan2.internal.SolarTime
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
}
