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

/** Pure text helpers of the workout views (units that are likely, not proven, carry "unconfirmed"). */
internal object WorkoutText {
    fun duration(sec: Int?): String =
        if (sec == null) "no data" else if (sec >= 3600) "%d:%02d:%02d".format(Locale.US, sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(Locale.US, sec / 60, sec % 60)

    fun pace(secPerKm: Int?): String = if (secPerKm == null) "no data" else "%d:%02d /km (unconfirmed)".format(Locale.US, secPerKm / 60, secPerKm % 60)

    fun distance(km: Double?): String = if (km == null) "no data" else "%.2f km (unconfirmed)".format(Locale.US, km)

    fun start(ms: Long, zone: ZoneId): String =
        if (ms <= 0) "time unknown" else DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** One line of the list: duration, distance, energy, average and highest heart rate. Gaps are left out, never shown as 0. */
    fun line(w: WorkoutRow): String = listOfNotNull(
        w.totalSec?.let { duration(it) }, w.distance?.let { "%.2f km (unconfirmed)".format(Locale.US, it) }, w.kcal?.let { "$it kcal" },
        w.hrAvg?.let { "avg $it bpm" }, w.hrMax?.let { "max $it bpm" },
    ).joinToString(", ").ifEmpty { "no summary" }

    /** The "Last workout" card: duration, energy, average and highest heart rate. Gaps are left out, never shown as 0. */
    fun lastLine(w: WorkoutRow): String = listOfNotNull(
        w.totalSec?.let { duration(it) }, w.kcal?.let { "$it kcal" }, w.hrAvg?.let { "avg $it bpm" }, w.hrMax?.let { "max $it bpm" },
    ).joinToString(", ").ifEmpty { "no summary" }

    /** The summary lines of the detail view. */
    fun lines(w: WorkoutRow): List<Pair<String, String>> = listOfNotNull(
        w.totalSec?.let { "Duration" to duration(it) }, w.distance?.let { "Distance" to distance(it) }, w.avgPace?.let { "Average pace" to pace(it) },
        w.avgSpeed?.let { "Average speed" to "%.1f km/h (unconfirmed)".format(Locale.US, it) }, w.kcal?.let { "Energy" to "$it kcal" },
        w.hrAvg?.let { "Average heart rate" to "$it bpm" }, w.hrMax?.let { "Highest heart rate" to "$it bpm" }, w.hrMin?.let { "Lowest heart rate" to "$it bpm" },
        w.ascent?.let { "Ascent" to "$it m" }, w.descent?.let { "Descent" to "$it m" }, w.cadenceAvg?.let { "Cadence" to "$it (unit unknown, unconfirmed)" },
    )

    fun lapLine(l: WorkoutLapRow): String = listOfNotNull(
        l.lapSec?.let { duration(it) }, l.distance?.let { "%.2f km".format(Locale.US, it) }, l.pace?.let { pace(it) }, l.hrAvg?.let { "$it bpm" },
    ).joinToString(", ").ifEmpty { "no data" }

    /** The note under the rows when a unit shown is not confirmed yet. */
    fun unitsNote(w: WorkoutRow): String? = null

    /** The pace chart's values. */
    fun paceSeries(d: WorkoutDetail): List<Float?> = series(d.samples) { it.pace }

    /** At most [max] evenly spread values of [pick] over the samples (a chart has no use for thousands of points); gaps stay null. */
    fun series(samples: List<WorkoutSampleRow>, max: Int = 120, pick: (WorkoutSampleRow) -> Number?): List<Float?> {
        if (samples.isEmpty()) return emptyList()
        val n = minOf(max, samples.size)
        return List(n) { i -> pick(samples[(i.toLong() * samples.size / n).toInt()])?.toFloat() }
    }
}
