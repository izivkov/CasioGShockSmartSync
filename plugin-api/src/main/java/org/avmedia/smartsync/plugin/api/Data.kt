package org.avmedia.smartsync.plugin.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The values the host read of a watch, by dotted key ([PluginHost.valueCatalog] lists every key with its kind, type and
 * unit). A value is a String, Boolean, Long (every whole number), Double (every fraction), a List or a Map of those,
 * the same after a restart as fresh; a key the watch does not have is absent. [readAtMillis] has an entry for every key
 * in [values]: when that value was read last (values keep their older reading when a later read did not give them).
 * The decoded watch settings are in [PluginHost.settings], by [SettingInfo.key].
 */
data class WatchValues(
    val watch: WatchId,
    val values: Map<String, Any?> = emptyMap(),
    val readAtMillis: Map<String, Long> = emptyMap(),
) {
    operator fun get(key: String): Any? = values[key]
}

/** One value key the host can read ([PluginHost.valueCatalog]). */
data class ValueInfo(
    val key: String,
    /** What it holds, in plain words (formats included). */
    val description: String,
    /** The kind of data it is: served only to a plugin that declared it ([PluginAccess.reads]). */
    val use: DataUse,
    val type: ValueType,
    /** The unit ("%", "°C", "bpm", "kcal", "m", "s", "min", "steps"); null for none. */
    val unit: String? = null,
)

/** The type of a value in [WatchValues]. */
enum class ValueType {
    TEXT,
    BOOL,
    /** A whole number (a Long). */
    WHOLE,
    /** A fraction (a Double). */
    NUMBER,
    /** A time stamp as ISO-8601 text; with an offset unless the description says "without offset". */
    DATE_TIME,
    /** A date as ISO-8601 text ("2026-11-24"). */
    DATE,
    /** A time of day, "HH:MM". */
    TIME,
    LIST,
    MAP,
}

/**
 * A span of phone local time: [from] included, [to] not ([to] exclusive), so adjoining ranges neither overlap nor leave
 * a gap. [from] equal to [to] is empty.
 */
data class HistoryRange(val from: LocalDateTime, val to: LocalDateTime) {
    init { require(!to.isBefore(from)) { "to is before from" } }

    fun contains(t: LocalDateTime): Boolean = !t.isBefore(from) && !t.isAfter(to)

    companion object {
        /** Whole days [first] to [last], both included: from the start of [first] to the start of the day after [last]. */
        fun days(first: LocalDate, last: LocalDate): HistoryRange = HistoryRange(first.atStartOfDay(), last.plusDays(1).atStartOfDay())
    }
}

/** A stored record ([PluginHost.history]). A null field is a gap: the watch gave no value (never read as 0). */
sealed interface HistoryRecord

/** What [PluginHost.history] can return, typed: `history(w, DataKind.Hours, range)` is a list of [HourRecord]. */
sealed class DataKind<T : HistoryRecord>(val name: String) {
    data object Days : DataKind<DayRecord>("days")
    data object Hours : DataKind<HourRecord>("hours")
    /** Heart rate samples (2 minutes apart on the DW-H5600 family). */
    data object HeartRate : DataKind<HeartRateSample>("heart_rate")
    data object Nights : DataKind<NightRecord>("nights")
    data object Workouts : DataKind<WorkoutRecord>("workouts")

    override fun toString(): String = name

    companion object {
        val ALL: List<DataKind<*>> get() = listOf(Days, Hours, HeartRate, Nights, Workouts)
    }
}

data class DayRecord(
    val date: LocalDate,
    val steps: Int? = null,
    val distanceM: Int? = null,
    val kcal: Int? = null,
    val activeMin: Int? = null,
    val walkSec: Int? = null,
    val runSec: Int? = null,
    val stillSec: Int? = null,
    /** Daily values: the watch gives no time for them. */
    val hrRest: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    /** Daily blood oxygen, percent; no time of measurement. */
    val spo2: Int? = null,
    val stairsUp: Int? = null,
    val stairsDown: Int? = null,
) : HistoryRecord

/** One hour: [start] is the start of the hour. */
data class HourRecord(val start: LocalDateTime, val steps: Int? = null, val heartRate: Int? = null) : HistoryRecord

data class HeartRateSample(val time: LocalDateTime, val bpm: Int) : HistoryRecord

/** One night, by the date it belongs to. [stages] is empty when none were read; [stagesConfirmed] says whether they are proven. */
data class NightRecord(
    val date: LocalDate,
    val score: Int? = null,
    val fellAsleep: LocalDateTime? = null,
    val wokeUp: LocalDateTime? = null,
    val totalMin: Int? = null,
    val deepMin: Int? = null,
    val lightMin: Int? = null,
    val remMin: Int? = null,
    val awakeMin: Int? = null,
    val heartRate: Int? = null,
    val breathing: Float? = null,
    val stages: List<SleepStage> = emptyList(),
    val stagesConfirmed: Boolean = false,
) : HistoryRecord

/** One stage from [start] on. [stage] is the word as stored; [kind] the same as a value (null for a word outside the four). */
data class SleepStage(val start: LocalDateTime, val stage: String) {
    val kind: SleepStageKind? get() = SleepStageKind.entries.firstOrNull { it.name.equals(stage.trim(), ignoreCase = true) }
}

enum class SleepStageKind { AWAKE, LIGHT, DEEP, REM }

/**
 * One workout. [id] is stable: the same workout read again has the same id. Fields named in [unconfirmed] have a unit
 * that is likely but not proven (a plugin that needs certainty leaves them out).
 */
data class WorkoutRecord(
    val id: String,
    val start: Instant,
    val end: Instant? = null,
    /** The watch's sport number and its name. */
    val sport: Int,
    val sportName: String,
    val totalSec: Int? = null,
    /** Distance as the watch gives it; its unit is not proven (likely km), so it is in [unconfirmed]. */
    val distance: Double? = null,
    /** Distance in meters, only when the unit is proven; null otherwise. */
    val distanceM: Double? = null,
    /** Energy of the workout as the watch gives it (total, not only active), kcal; the scale is in [unconfirmed] while not proven. */
    val kcal: Long? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val ascent: Long? = null,
    val descent: Long? = null,
    /** Property names ([DISTANCE], [KCAL], ...) whose unit is likely but not proven. */
    val unconfirmed: Set<String> = emptySet(),
) : HistoryRecord {
    companion object {
        const val DISTANCE = "distance"
        const val KCAL = "kcal"
    }
}
