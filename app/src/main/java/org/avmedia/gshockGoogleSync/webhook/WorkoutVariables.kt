package org.avmedia.gshockGoogleSync.webhook

import java.time.Instant
import java.time.ZoneOffset
import org.avmedia.gshockapi.model.WorkoutSession

/** The `workout.*` variables: the last workout downloaded from a DW-H5600 (null until one was read this session). */
object WorkoutVariables {
    /** name to description, in display order. Units the notes mark as likely say "unconfirmed". */
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

    fun values(s: WorkoutSession?): Map<String, Any?> = linkedMapOf(
        "workout.sport" to s?.sportName,
        "workout.start" to s?.startUtcMillis?.let { Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).withNano(0).toString() },
        "workout.duration_seconds" to s?.summary?.totalSeconds,
        "workout.distance" to s?.summary?.distance,
        "workout.kcal" to s?.summary?.kcal,
        "workout.hr_avg" to s?.summary?.hrAvg,
        "workout.hr_max" to s?.summary?.hrMax,
        "workout.unconfirmed" to s?.unconfirmed?.entries?.joinToString("; ") { "${it.key}: ${it.value}" },
    )
}
