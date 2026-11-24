package org.avmedia.smartsync.plugin.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The values the host read of a watch, by dotted key ("watch.battery", "health.steps_today", "watch.settings.*",
 * "watch.setting.*", "phone.*", "app.*"). A value is a String, Boolean, Int, Long, Double, a List or a Map of those;
 * a key the watch does not have is absent. [readAtMillis] is when each value was read.
 */
data class WatchValues(
    val watch: WatchId,
    val values: Map<String, Any?> = emptyMap(),
    val readAtMillis: Map<String, Long> = emptyMap(),
) {
    operator fun get(key: String): Any? = values[key]
}

/** A span of phone local time, both ends included. */
data class HistoryRange(val from: LocalDateTime, val to: LocalDateTime) {
    init { require(!to.isBefore(from)) { "to is before from" } }

    fun contains(t: LocalDateTime): Boolean = !t.isBefore(from) && !t.isAfter(to)

    companion object {
        /** Whole days [first] to [last]. */
        fun days(first: LocalDate, last: LocalDate): HistoryRange = HistoryRange(first.atStartOfDay(), last.atTime(23, 59, 59))
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
    val hrRest: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
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

data class SleepStage(val start: LocalDateTime, val stage: String)

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
    val distance: Double? = null,
    val kcal: Long? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val ascent: Long? = null,
    val descent: Long? = null,
    val unconfirmed: Set<String> = emptySet(),
) : HistoryRecord
