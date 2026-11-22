package org.avmedia.gshockGoogleSync.webhook

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sent values in plain words, per group. Pure: the zone is passed in. */
object HaSentView {
    private val whenFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

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
            runCatching { OffsetDateTime.parse(v).atZoneSameInstant(zone).format(whenFormat) }.getOrDefault(v)
        v is Number -> (if (v.toDouble() % 1.0 == 0.0) v.toLong().toString() else v.toString()) + (s.unit?.let { " $it" } ?: "")
        else -> v.toString()
    }

    fun time(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String? =
        if (atMillis <= 0) null else java.time.Instant.ofEpochMilli(atMillis).atZone(zone).format(whenFormat)
}
