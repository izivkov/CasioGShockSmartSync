package org.avmedia.gshockGoogleSync.history

import com.google.gson.GsonBuilder
import org.avmedia.gshockapi.model.WorkoutUnits

/** Workouts as text for the share sheet. Gaps are empty cells in the CSV and null in the JSON, never 0. */
object WorkoutExport {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    private val WORKOUT_COLS = listOf(
        "start_utc_ms", "sport", "sport_name", "end_utc_ms", "total_sec", "distance_unconfirmed", "avg_pace_unconfirmed",
        "avg_speed_unconfirmed", "kcal", "hr_avg", "hr_max", "hr_min", "ascent", "descent", "cadence_avg_unconfirmed", "laps", "has_gps",
    )
    private val LAP_COLS = listOf(
        "start_utc_ms", "sport", "lap", "lap_sec", "manual", "distance_unconfirmed", "pace_unconfirmed", "speed_unconfirmed",
        "hr_avg", "hr_max", "hr_min", "ascent", "descent", "cadence_unconfirmed",
    )
    private val SAMPLE_COLS = listOf(
        "start_utc_ms", "sport", "n", "elapsed_sec", "paused_unconfirmed", "pace_unconfirmed", "hr", "altitude", "cadence_unconfirmed",
        "latitude", "longitude", "speed_unconfirmed", "total_distance_unconfirmed",
    )

    /** Three tables in one file, each led by a "# name" line and its header. Column names say "unconfirmed" where the unit is. */
    fun csv(workouts: List<WorkoutRow>, laps: List<WorkoutLapRow>, samples: List<WorkoutSampleRow>): String = buildString {
        table("workouts", WORKOUT_COLS, workouts.map {
            listOf(it.startMs, it.sport, it.sportName, it.endMs, it.totalSec, it.distance, it.avgPace, it.avgSpeed, it.kcal, it.hrAvg, it.hrMax, it.hrMin, it.ascent, it.descent, it.cadenceAvg, it.lapCount, it.hasGps)
        })
        append('\n')
        table("laps", LAP_COLS, laps.map {
            listOf(it.startMs, it.sport, it.idx, it.lapSec, it.manual, it.distance, it.pace, it.speed, it.hrAvg, it.hrMax, it.hrMin, it.ascent, it.descent, it.cadence)
        })
        append('\n')
        table("samples", SAMPLE_COLS, samples.map {
            listOf(it.startMs, it.sport, it.idx, it.elapsedSec, it.paused, it.pace, it.hr, it.altitude, it.cadence, it.latitude, it.longitude, it.speed, it.totalDistance)
        })
    }

    fun json(workouts: List<WorkoutRow>, laps: List<WorkoutLapRow>, samples: List<WorkoutSampleRow>): String =
        gson.toJson(linkedMapOf("unconfirmed" to WorkoutUnits.UNCONFIRMED, "workouts" to workouts, "laps" to laps, "samples" to samples))

    private fun StringBuilder.table(name: String, cols: List<String>, rows: List<List<Any?>>) {
        append("# ").append(name).append('\n').append(cols.joinToString(",")).append('\n')
        rows.forEach { r -> append(r.joinToString(",") { cell(it) }).append('\n') }
    }

    private fun cell(v: Any?): String = when (v) {
        null -> ""
        is String -> if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        else -> v.toString()
    }
}
