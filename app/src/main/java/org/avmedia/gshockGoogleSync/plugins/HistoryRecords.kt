package org.avmedia.gshockGoogleSync.plugins

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockGoogleSync.history.DayRow
import org.avmedia.gshockGoogleSync.history.HourRow
import org.avmedia.gshockGoogleSync.history.HrSlotRow
import org.avmedia.gshockGoogleSync.history.NightRow
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.DayRecord
import org.avmedia.smartsync.plugin.api.HeartRateSample
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.HistoryRecord
import org.avmedia.smartsync.plugin.api.HourRecord
import org.avmedia.smartsync.plugin.api.NightRecord
import org.avmedia.smartsync.plugin.api.SleepStage
import org.avmedia.smartsync.plugin.api.WorkoutRecord

/** The stored rows of one watch, as the plugin interface's records ([DataKind]), filtered by range. Android-free. */
object HistoryRecords {
    /** What a store gives for [HistoryRecords.read]. */
    interface Rows {
        fun days(): List<DayRow>
        fun hours(): List<HourRow>
        fun hrSlots(date: String): List<HrSlotRow>
        fun nights(): List<NightRow>
        fun workouts(): List<WorkoutRow>
    }

    /** Field names of a workout whose unit is likely but not proven. */
    var unconfirmedWorkoutFields: Set<String> = setOf("distance", "kcal")

    @Suppress("UNCHECKED_CAST")
    fun <T : HistoryRecord> read(rows: Rows, kind: DataKind<T>, range: HistoryRange, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<T> =
        when (kind) {
            DataKind.Days -> rows.days().mapNotNull(::day).filter { inDays(it.date, range) }
            DataKind.Hours -> rows.hours().mapNotNull(::hour).filter { range.contains(it.start) }
            DataKind.HeartRate -> datesOf(range).flatMap { d -> rows.hrSlots(d.toString()).map { heartRate(d, it) } }.filter { range.contains(it.time) }
            DataKind.Nights -> rows.nights().mapNotNull(::night).filter { inDays(it.date, range) }
            DataKind.Workouts -> rows.workouts().map(::workout).filter { range.contains(LocalDateTime.ofInstant(it.start, zone)) }.sortedBy { it.start }
        }.sortedWith(compareBy { key(it) }) as List<T>

    private fun key(r: HistoryRecord): String = when (r) {
        is DayRecord -> r.date.toString()
        is HourRecord -> r.start.toString()
        is HeartRateSample -> r.time.toString()
        is NightRecord -> r.date.toString()
        is WorkoutRecord -> r.start.toString()
    }

    private fun inDays(d: LocalDate, range: HistoryRange) = !d.isBefore(range.from.toLocalDate()) && !d.isAfter(range.to.toLocalDate())

    private fun datesOf(range: HistoryRange): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = range.from.toLocalDate()
        while (!d.isAfter(range.to.toLocalDate()) && out.size < 400) { out += d; d = d.plusDays(1) }
        return out
    }

    fun day(r: DayRow): DayRecord? = runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { d ->
        DayRecord(d, r.steps, r.distanceM, r.kcal, r.activeMin, r.walkSec, r.runSec, r.stillSec, r.hrRest, r.hrMin, r.hrMax, r.spo2, r.stairsUp, r.stairsDown)
    }

    fun hour(r: HourRow): HourRecord? = runCatching { LocalDate.parse(r.date) }.getOrNull()
        ?.takeIf { r.hour in 0..23 }?.let { HourRecord(it.atTime(r.hour, 0), r.steps, r.hr) }

    fun heartRate(date: LocalDate, r: HrSlotRow): HeartRateSample = HeartRateSample(date.atStartOfDay().plusMinutes(r.minute.toLong()), r.bpm)

    fun night(r: NightRow): NightRecord? = runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { d ->
        NightRecord(
            d, r.score, time(r.fellAsleep), time(r.wokeUp), r.totalMin, r.deepMin, r.lightMin, r.remMin, r.awakeMin, r.hr, r.breathing,
            stages = r.stages.orEmpty().lines().mapNotNull { line ->
                val t = time(line.substringBefore(',', ""))
                val s = line.substringAfter(',', "").trim()
                if (t == null || s.isEmpty()) null else SleepStage(t, s)
            },
            // The stage times are not yet checked on a real night.
            stagesConfirmed = false,
        )
    }

    fun workout(r: WorkoutRow): WorkoutRecord = WorkoutRecord(
        id = "${r.startMs}-${r.sport}",
        start = Instant.ofEpochMilli(r.startMs),
        end = r.endMs?.let(Instant::ofEpochMilli),
        sport = r.sport,
        sportName = r.sportName,
        totalSec = r.totalSec,
        distance = r.distance,
        kcal = r.kcal,
        hrAvg = r.hrAvg,
        hrMax = r.hrMax,
        hrMin = r.hrMin,
        ascent = r.ascent,
        descent = r.descent,
        unconfirmed = unconfirmedWorkoutFields,
    )

    private fun time(s: String?): LocalDateTime? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}
