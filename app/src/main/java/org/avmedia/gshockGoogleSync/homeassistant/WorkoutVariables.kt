package org.avmedia.gshockGoogleSync.homeassistant

import java.time.Instant
import java.time.ZoneOffset
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockapi.model.WorkoutUnits

/** The `workout.*` variables: the newest workout stored from a DW-H5600 (null while none is stored). */
object WorkoutVariables {
    /** name to description, in display order. Units that are likely, not proven, say "unconfirmed". */
    val DESCRIPTIONS: Map<String, String> = linkedMapOf(
        "workout.sport" to "Sport of the last workout, for example Running",
        "workout.start" to "Start of the last workout, ISO 8601 UTC (the watch's time zone offset is unconfirmed)",
        "workout.duration_seconds" to "Duration of the last workout, seconds",
        "workout.distance" to "Distance of the last workout (UNCONFIRMED: likely km)",
        "workout.kcal" to "Energy of the last workout, kcal (UNCONFIRMED: scale not verified)",
        "workout.hr_avg" to "Average heart rate of the last workout, bpm",
        "workout.hr_max" to "Highest heart rate of the last workout, bpm",
        "workout.unconfirmed" to "Values of the last workout whose unit or meaning is unconfirmed, as 'name: description' joined by semicolons",
    )

    val NAMES: List<String> = DESCRIPTIONS.keys.toList()

    fun values(w: WorkoutRow?): Map<String, Any?> = linkedMapOf(
        "workout.sport" to w?.sportName,
        "workout.start" to w?.startMs?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).withNano(0).toString() },
        "workout.duration_seconds" to w?.totalSec,
        "workout.distance" to w?.distance,
        "workout.kcal" to w?.kcal,
        "workout.hr_avg" to w?.hrAvg,
        "workout.hr_max" to w?.hrMax,
        "workout.unconfirmed" to w?.let { WorkoutUnits.UNCONFIRMED.entries.joinToString("; ") { e -> "${e.key}: ${e.value}" } },
    )
}
