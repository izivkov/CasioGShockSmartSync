package org.avmedia.gshockGoogleSync.webhook

import java.time.OffsetDateTime
import java.time.ZoneId

/** The sent values in plain words, per group. Pure: the zone is passed in. */
object HaSentView {
    /** Set once at start (App) from the phone: null in tests, which then use 24 h. */
    var appContext: android.content.Context? = null
    private fun locale() = java.util.Locale.getDefault()
    private fun is24h() = appContext?.let { android.text.format.DateFormat.is24HourFormat(it) } ?: true


    fun groups(sent: HaSent, on: Set<HaGroup>, zone: ZoneId = ZoneId.systemDefault()): List<HaGroupUi> =
        HaGroup.entries.map { g ->
            val rows = HaGroups.sensors(setOf(g)).mapNotNull { s ->
                sent.states[s.uniqueId]?.let { s.name to value(s, it, zone) }
            }
            HaGroupUi(g, g in on, rows)
        }

    fun value(s: HaSensor, v: Any, zone: ZoneId = ZoneId.systemDefault()): String = when {
        v is Boolean -> if (v) "Yes" else "No"
        s.deviceClass == "timestamp" && v is String ->
            runCatching { ShortDateTime.format(OffsetDateTime.parse(v).toInstant().toEpochMilli(), locale(), is24h(), zone) }.getOrDefault(v)
        v is Number -> (if (v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()) + (s.unit?.let { " $it" } ?: "")
        else -> v.toString()
    }

    fun time(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String? =
        ShortDateTime.formatOrNull(atMillis, locale(), is24h(), zone)
}
