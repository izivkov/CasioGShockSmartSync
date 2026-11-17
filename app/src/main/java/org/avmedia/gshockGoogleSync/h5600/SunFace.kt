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

    private const val PREFS = org.avmedia.gshockGoogleSync.h5600.H5600Prefs.NAME
    private const val KEY_ENABLED = "dyn_sun_face_enabled"

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    /**
     * Sunset for [date] at [lat]/[lon] in [zone] (sunrise equation, NOAA-style approximation,
     * accurate to about a minute at mid latitudes). Null during polar day / night.
     */
    fun sunset(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): LocalDateTime? =
        // Same formula as the weather trick (one copy), with the standard -0.833° horizon.
        TempSun.riseSetUtcMin(date, lat, lon, h0 = -0.833)?.let { (_, set) -> TempSun.toLocal(date, set, zone) }

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
