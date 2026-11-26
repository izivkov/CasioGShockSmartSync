package org.avmedia.gshockGoogleSync.ui.health.history

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.avmedia.gshockGoogleSync.history.WorkoutLapRow
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.history.WorkoutSampleRow

/** One opened workout with its laps and samples. */
data class WorkoutDetail(val workout: WorkoutRow, val laps: List<WorkoutLapRow>, val samples: List<WorkoutSampleRow>)

/**
 * What the Workouts views show. The Health screen shows the "Last workout" card; a tap opens the list ([listOpen]),
 * [selected] is the workout opened from the list. Workouts come with every Health read: no read button here.
 */
data class WorkoutsUi(
    val workouts: List<WorkoutRow> = emptyList(),
    val selected: WorkoutDetail? = null,
    val listOpen: Boolean = false,
    val exportNote: String = "",
    val zone: ZoneId = ZoneId.systemDefault(),
)

class WorkoutEvents(
    val onSelect: (WorkoutRow?) -> Unit = {},
    val onOpenList: (Boolean) -> Unit = {},
    val onExportCsv: () -> Unit = {},
    val onExportJson: () -> Unit = {},
)

/**
 * Pure text helpers of the workout views. Units that are likely but not proven (distance, pace, speed, cadence) get no
 * mark on each row: one note under the rows says so ([unitsNote]). Zero or missing values are left out, never "0".
 */
internal object WorkoutText {
    const val UNITS_NOTE = "Some units are not confirmed yet."

    fun duration(sec: Int?): String =
        if (sec == null) "no data" else if (sec >= 3600) "%d:%02d:%02d".format(Locale.US, sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(Locale.US, sec / 60, sec % 60)

    fun pace(secPerKm: Int?): String = if (secPerKm == null) "no data" else "%d:%02d /km".format(Locale.US, secPerKm / 60, secPerKm % 60)

    fun distance(km: Double?): String = if (km == null) "no data" else "%.2f km".format(Locale.US, km)

    /** The value when it is there and above zero: a 25 s walk without GPS has distance, pace and speed 0. */
    private fun <T : Number> T?.shown(): T? = this?.takeIf { it.toDouble() > 0.0 }

    /** Whether the workout has a distance: without one, pace and speed mean nothing. */
    fun hasDistance(w: WorkoutRow): Boolean = w.distance.shown() != null

    fun start(ms: Long, zone: ZoneId): String =
        if (ms <= 0) "time unknown" else DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** One line of the list: duration, distance, energy, average and highest heart rate. Gaps are left out, never shown as 0. */
    fun line(w: WorkoutRow): String = listOfNotNull(
        w.totalSec?.let { duration(it) }, w.distance.shown()?.let { distance(it) }, w.kcal?.let { "$it kcal" },
        w.hrAvg?.let { "avg $it bpm" }, w.hrMax?.let { "max $it bpm" },
    ).joinToString(", ").ifEmpty { "no summary" }

    /** The "Last workout" card: duration, energy, average and highest heart rate. Gaps are left out, never shown as 0. */
    fun lastLine(w: WorkoutRow): String = listOfNotNull(
        w.totalSec?.let { duration(it) }, w.kcal?.let { "$it kcal" }, w.hrAvg?.let { "avg $it bpm" }, w.hrMax?.let { "max $it bpm" },
    ).joinToString(", ").ifEmpty { "no summary" }

    /**
     * The summary lines of the detail view: the watch's summary values as they are. Distance, pace, speed, ascent,
     * descent and cadence only when above zero; pace and speed only with a distance.
     */
    fun lines(w: WorkoutRow): List<Pair<String, String>> {
        val moved = hasDistance(w)
        return listOfNotNull(
            w.totalSec?.let { "Duration" to duration(it) }, w.distance.shown()?.let { "Distance" to distance(it) },
            w.avgPace.shown()?.takeIf { moved }?.let { "Average pace" to pace(it) },
            w.avgSpeed.shown()?.takeIf { moved }?.let { "Average speed" to "%.1f km/h".format(Locale.US, it) }, w.kcal?.let { "Energy" to "$it kcal" },
            w.hrAvg?.let { "Average heart rate" to "$it bpm" }, w.hrMax?.let { "Highest heart rate" to "$it bpm" }, w.hrMin?.let { "Lowest heart rate" to "$it bpm" },
            w.ascent.shown()?.let { "Ascent" to "$it m" }, w.descent.shown()?.let { "Descent" to "$it m" }, w.cadenceAvg.shown()?.let { "Cadence" to "$it" },
        )
    }

    /** [UNITS_NOTE] when a row with a unit not confirmed yet (distance, pace, speed, cadence) is shown, else null. */
    fun unitsNote(w: WorkoutRow): String? =
        UNITS_NOTE.takeIf { lines(w).any { it.first in UNCONFIRMED_ROWS } }

    private val UNCONFIRMED_ROWS = setOf("Distance", "Average pace", "Average speed", "Cadence")

    fun lapLine(l: WorkoutLapRow): String = listOfNotNull(
        l.lapSec?.let { duration(it) }, l.distance.shown()?.let { distance(it) }, l.pace.shown()?.let { pace(it) }, l.hrAvg?.let { "$it bpm" },
    ).joinToString(", ").ifEmpty { "no data" }

    /** The pace chart's values: none without a distance, and a pace of 0 (standing still, no GPS) is a gap. */
    fun paceSeries(d: WorkoutDetail): List<Float?> =
        if (!hasDistance(d.workout)) emptyList() else series(d.samples) { it.pace?.takeIf { p -> p > 0 } }

    /** At most [max] evenly spread values of [pick] over the samples (a chart has no use for thousands of points); gaps stay null. */
    fun series(samples: List<WorkoutSampleRow>, max: Int = 120, pick: (WorkoutSampleRow) -> Number?): List<Float?> {
        if (samples.isEmpty()) return emptyList()
        val n = minOf(max, samples.size)
        return List(n) { i -> pick(samples[(i.toLong() * samples.size / n).toInt()])?.toFloat() }
    }
}
