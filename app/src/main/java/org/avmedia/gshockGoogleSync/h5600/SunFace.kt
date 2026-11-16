package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.avmedia.gshockapi.model.WatchFaceItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Dynamic sunset-step toggle": the watch face shows Steps, except from [LEAD_HOURS] before
 * sunset until [TRAIL_MIN] minutes after it, when it shows Sun data.
 */
object SunFace {
    const val LEAD_HOURS = 4L
    const val TRAIL_MIN = 10L

    private const val PREFS = "h5600_prefs"
    private const val KEY_ENABLED = "dyn_sun_face_enabled"

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    /**
     * Sunset for [date] at [lat]/[lon] in [zone] (sunrise equation, NOAA-style approximation,
     * accurate to about a minute at mid latitudes). Null during polar day / night.
     */
    fun sunset(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): LocalDateTime? {
        val rad = Math.PI / 180.0
        val jDate = date.toEpochDay() + 2440587.5 + 0.5 // Julian date at noon UTC
        val n = Math.round(jDate - 2451545.0 + 0.0008).toDouble()
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val c = 1.9148 * sin(m * rad) + 0.02 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lambda = (m + c + 180.0 + 102.9372).mod(360.0)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
        val decl = asin(sin(lambda * rad) * sin(23.4397 * rad))
        val cosW = (sin(-0.833 * rad) - sin(lat * rad) * sin(decl)) / (cos(lat * rad) * cos(decl))
        if (cosW < -1.0 || cosW > 1.0) return null
        val jSet = jTransit + acos(cosW) / rad / 360.0
        val epochMs = ((jSet - 2440587.5) * 86_400_000.0).toLong()
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    }

    /** Pure: which face the watch should show now. */
    fun desiredFace(now: LocalDateTime, lat: Double, lon: Double, zone: ZoneId): WatchFaceItem {
        val set = sunset(now.toLocalDate(), lat, lon, zone) ?: return WatchFaceItem.STEPS
        return if (!now.isBefore(set.minusHours(LEAD_HOURS)) && now.isBefore(set.plusMinutes(TRAIL_MIN))) WatchFaceItem.SUNRISE_SUNSET
        else WatchFaceItem.STEPS
    }

    /** Next moment the face may change (today's or tomorrow's window start/end). */
    fun nextSwitch(now: LocalDateTime, lat: Double, lon: Double, zone: ZoneId): LocalDateTime? =
        listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))
            .mapNotNull { sunset(it, lat, lon, zone) }
            .flatMap { listOf(it.minusHours(LEAD_HOURS), it.plusMinutes(TRAIL_MIN)) }
            .filter { it.isAfter(now) }
            .minOrNull()
}
