package org.avmedia.gshockGoogleSync.homeassistant

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The one short date and time form of the app, e.g. "14 Nov, 22:13": order from the locale, 12/24 h from the phone. */
object ShortDateTime {
    fun format(atMillis: Long, locale: Locale, is24h: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val skeleton = if (is24h) "dMMMHm" else "dMMMhm"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        return DateTimeFormatter.ofPattern(pattern, locale).format(Instant.ofEpochMilli(atMillis).atZone(zone))
    }

    fun formatOrNull(atMillis: Long, locale: Locale, is24h: Boolean, zone: ZoneId = ZoneId.systemDefault()): String? =
        if (atMillis <= 0) null else format(atMillis, locale, is24h, zone)

    fun format(context: Context, atMillis: Long): String =
        format(atMillis, Locale.getDefault(), DateFormat.is24HourFormat(context))
}
