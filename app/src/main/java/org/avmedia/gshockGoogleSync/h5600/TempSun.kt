package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Temperature instead of sunrise": the watch computes sunrise/sunset from the 0x24 coordinates,
 * so we send fake coordinates whose sunrise is 0:TT local (TT = outdoor °C, 0..59) and whose
 * sunset is the real sunset at the phone's location.
 *
 * Longitude sets solar noon (the midpoint of sunrise and sunset), latitude sets the day length.
 * Same sunrise equation as [SunFace]; the watch's own formula may differ by about a minute.
 */
object TempSun {
    const val MAX_TEMP = 59
    /** Open-Meteo "current" values are 15-minute data (interval 900 s); fetch once per quarter. */
    const val REFRESH_MIN = 15L

    /**
     * Next fetch time: 1 minute after the next quarter hour (Open-Meteo publishes at :00/:15/:30/:45),
     * so each fetch picks up a new value. Pure.
     */
    fun nextFetch(now: LocalDateTime): LocalDateTime {
        val q = now.withSecond(0).withNano(0).withMinute((now.minute / 15) * 15).plusMinutes(1)
        return if (q.isAfter(now)) q else q.plusMinutes(15)
    }

    private const val PREFS = "h5600_prefs"
    private const val KEY_ENABLED = "temp_sun_enabled"
    private const val KEY_TEMP = "temp_sun_last_c"
    private const val KEY_TEMP_AT = "temp_sun_last_at"
    // v2 keys: the old hand-tuned corrections (+2 / -3) are replaced by WATCH_H0 below.
    private const val KEY_RISE_CORR = "temp_sun_rise_corr_v2"
    private const val KEY_SET_CORR = "temp_sun_set_corr_v2"

    /** Standard sunrise/sunset horizon angle (refraction + solar radius). */
    const val STANDARD_H0 = -0.833

    /**
     * Horizon angle the DW-H5600 itself appears to use: fitted to two readings on the user's watch
     * (2026-11-16: 60.1°S -> 0:16/17:26 and 35.6°S -> 3:19/17:21); explains both within ~1 min,
     * where the standard angle was off by 2-5 min depending on latitude.
     */
    const val WATCH_H0 = -1.0

    /** Aim this far into the target minute so the watch's rounding cannot tip it to a neighbour. */
    private const val AIM_INTO_MINUTE = 0.25

    const val DEFAULT_RISE_CORR = 0
    const val DEFAULT_SET_CORR = 0

    fun riseCorrection(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_RISE_CORR, DEFAULT_RISE_CORR)

    fun setCorrection(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SET_CORR, DEFAULT_SET_CORR)

    fun saveCorrections(context: Context, rise: Int, set: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RISE_CORR, rise.coerceIn(-15, 15)).putInt(KEY_SET_CORR, set.coerceIn(-15, 15)).apply()

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Last fetched temperature and when (epoch ms), or null. */
    fun last(context: Context): Pair<Double, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong(KEY_TEMP_AT, 0L)
        return if (at == 0L) null else p.getFloat(KEY_TEMP, 0f).toDouble() to at
    }

    private fun store(context: Context, c: Double) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_TEMP, c.toFloat()).putLong(KEY_TEMP_AT, System.currentTimeMillis()).apply()

    /** Weather category from the WMO code only (fallback when amounts are missing). */
    fun category(wmoCode: Int): Int = when (wmoCode) {
        0, 1 -> 1                    // clear, mainly clear
        2, 3, 45, 48 -> 2            // partly cloudy, overcast, fog
        else -> 3                    // drizzle, rain, snow, showers, thunderstorm
    }

    /**
     * Weather category shown as the sunrise HOUR: 1 sunny, 2 cloudy, 3 rain.
     * Rain if the weather code says precipitation (51+) OR an amount is reported: on 2026-11-16 the
     * code said "light rain" with 0.0 mm and it really was raining at the user's place.
     */
    fun category(
        wmoCode: Int, precipitationMm: Double, cloudCoverPct: Int,
        windKmh: Double = 0.0, gustsKmh: Double = 0.0,
    ): Int = when {
        // 4 = extreme: heavy rain / violent showers / heavy snow / freezing rain, thunderstorms,
        // > 2 mm per 15 min (~8 mm/h, "heavy rain"), gusts >= 60 km/h (gale) or wind >= 40 km/h.
        wmoCode in setOf(65, 67, 75, 82, 86) || wmoCode >= 95 ||
            precipitationMm > 2.0 || gustsKmh >= 60.0 || windKmh >= 40.0 -> 4
        wmoCode >= 51 || precipitationMm > 0.0 -> 3
        wmoCode <= 1 || cloudCoverPct <= 30 -> 1
        else -> 2
    }

    private const val KEY_CAT = "temp_sun_last_cat"

    fun lastCategory(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CAT, -1).takeIf { it in 1..4 }

    /** Due for a fetch: older than [REFRESH_MIN], never fetched, or no weather code yet (W:TT needs it). */
    fun isStale(context: Context): Boolean {
        if (lastCategory(context) == null) return true
        val (_, at) = last(context) ?: return true
        val fetchedAt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), ZoneId.systemDefault())
        return !LocalDateTime.now().isBefore(nextFetch(fetchedAt)) // a newer quarter-hour value exists
    }

    /**
     * Blocking HTTPS call to Open-Meteo (call off the main thread). Sends only the coarse location
     * (2 decimals). Two attempts; on failure returns the last value if it is less than 3 h old.
     */
    fun fetchTemperature(context: Context, lat: Double, lon: Double): Double {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                return fetchOnce(context, lat, lon).also { store(context, it); Timber.i("TempSun: Open-Meteo %.1f °C".format(it)) }
            } catch (e: Exception) {
                lastError = e
                Timber.w("TempSun: attempt ${attempt + 1} failed: ${e.message}")
                Thread.sleep(2_000)
            }
        }
        last(context)?.takeIf { System.currentTimeMillis() - it.second < 3 * 3600_000L }?.let {
            Timber.w("TempSun: using last value %.1f °C".format(it.first))
            return it.first
        }
        throw lastError ?: IllegalStateException("no temperature")
    }

    private fun fetchOnce(context: Context, lat: Double, lon: Double): Double {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,precipitation,cloud_cover,wind_speed_10m,wind_gusts_10m"
                .format(java.util.Locale.US, lat, lon),
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("User-Agent", "GShockSmartSync-fork")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) error("HTTP $code")
            val body = conn.inputStream.bufferedReader().readText()
            val cur = JSONObject(body).getJSONObject("current")
            val cat = category(
                cur.optInt("weather_code", 3),
                cur.optDouble("precipitation", 0.0),
                cur.optInt("cloud_cover", 100),
                cur.optDouble("wind_speed_10m", 0.0),
                cur.optDouble("wind_gusts_10m", 0.0),
            )
            Timber.i("TempSun: code=${cur.optInt("weather_code")} precip=${cur.optDouble("precipitation")} " +
                "clouds=${cur.optInt("cloud_cover")} wind=${cur.optDouble("wind_speed_10m")} gusts=${cur.optDouble("wind_gusts_10m")} -> $cat")
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_CAT, cat).apply()
            return cur.getDouble("temperature_2m")
        } finally {
            conn.disconnect()
        }
    }

    // ---- pure solar math ----
    private const val R = Math.PI / 180.0

    private data class Parts(val jTransit: Double, val decl: Double)

    private fun parts(date: LocalDate, lon: Double): Parts {
        val jDate = date.toEpochDay() + 2440588.0
        val n = Math.round(jDate - 2451545.0 + 0.0008).toDouble()
        val js = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * js).mod(360.0)
        val c = 1.9148 * sin(m * R) + 0.02 * sin(2 * m * R) + 0.0003 * sin(3 * m * R)
        val lam = (m + c + 282.9372).mod(360.0)
        val jt = 2451545.0 + js + 0.0053 * sin(m * R) - 0.0069 * sin(2 * lam * R)
        return Parts(jt, asin(sin(lam * R) * sin(23.4397 * R)))
    }

    private fun base(date: LocalDate) = date.toEpochDay() + 2440587.5

    /** Sunrise / sunset as minutes after UTC midnight of [date] (may be < 0); null in polar day/night. */
    fun riseSetUtcMin(date: LocalDate, lat: Double, lon: Double, h0: Double = STANDARD_H0): Pair<Double, Double>? {
        val p = parts(date, lon)
        val cw = (sin(h0 * R) - sin(lat * R) * sin(p.decl)) / (cos(lat * R) * cos(p.decl))
        if (cw < -1 || cw > 1) return null
        val w = acos(cw) / R / 360.0
        return (p.jTransit - w - base(date)) * 1440.0 to (p.jTransit + w - base(date)) * 1440.0
    }

    /** UTC-midnight-relative minutes -> local wall time. */
    fun toLocal(date: LocalDate, utcMin: Double, zone: ZoneId): LocalDateTime =
        date.atStartOfDay().plusSeconds((utcMin * 60).roundToInt().toLong())
            .atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()

    /**
     * Fake coordinates for [date]: sunrise at local 0:[tempC] and the real sunset of [realLat]/[realLon].
     * Null if impossible (should not happen for 0..59 at normal latitudes).
     */
    fun fakeCoordinates(
        date: LocalDate, realLat: Double, realLon: Double, zone: ZoneId, tempC: Int,
        riseCorrMin: Int = 0, setCorrMin: Int = 0, riseHour: Int = 0,
    ): Pair<Double, Double>? {
        val t = tempC.coerceIn(0, MAX_TEMP)
        val h = riseHour.coerceIn(0, 9)
        // Corrections compensate the watch's own formula (see DEFAULT_RISE_CORR / DEFAULT_SET_CORR).
        // Target: the real sunset (standard formula), as the watch will compute it from the fake position.
        val setUtc = (riseSetUtcMin(date, realLat, realLon)?.second ?: return null) + setCorrMin + AIM_INTO_MINUTE
        // local 0:TT on [date] -> minutes after UTC midnight of [date]
        val offsetMin = zone.rules.getOffset(date.atTime(h, t)).totalSeconds / 60.0
        val riseUtc = h * 60 + t - offsetMin + riseCorrMin + AIM_INTO_MINUTE
        val noon = (riseUtc + setUtc) / 2
        val half = (setUtc - riseUtc) / 2
        if (half <= 0 || half >= 720) return null

        var lon = realLon
        repeat(30) {
            val p = parts(date, lon)
            lon += ((p.jTransit - base(date)) * 1440.0 - noon) / 4.0
            lon = ((lon + 180).mod(360.0)) - 180
        }
        val decl = parts(date, lon).decl
        val target = cos(half / 4.0 * R)
        // Solve the latitude in the watch's own model (WATCH_H0), so its display hits the target.
        val g = { la: Double -> (sin(WATCH_H0 * R) - sin(la * R) * sin(decl)) / (cos(la * R) * cos(decl)) - target }
        var lo = -89.9; var hi = 89.9
        if (g(lo) * g(hi) > 0) return null
        repeat(80) {
            val mid = (lo + hi) / 2
            if (g(lo) * g(mid) <= 0) hi = mid else lo = mid
        }
        return (lo + hi) / 2 to lon
    }
}
