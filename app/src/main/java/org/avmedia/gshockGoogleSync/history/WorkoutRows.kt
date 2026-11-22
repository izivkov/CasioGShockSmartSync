package org.avmedia.gshockGoogleSync.history

import androidx.room.Entity

/**
 * One workout of the watch. Key = start (UTC millis) and sport: the same workout read again is the same row.
 * A null column is a gap (the watch gave no value), never 0. Distance, pace, speed and stride units are unconfirmed.
 */
@Entity(tableName = "workout", primaryKeys = ["startMs", "sport"])
data class WorkoutRow(
    val startMs: Long,
    val sport: Int,
    val sportName: String,
    val endMs: Long? = null,
    val utcOffsetRaw: Int = 0,
    val hasGps: Boolean = false,
    val totalSec: Int? = null,
    val distance: Double? = null,
    val avgPace: Int? = null,
    val avgSpeed: Double? = null,
    val kcal: Long? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val ascent: Long? = null,
    val descent: Long? = null,
    val cadenceAvg: Int? = null,
    val lapCount: Int? = null,
    /** Warnings of the decoder, one per line; null = none. */
    val warnings: String? = null,
    val savedAtMs: Long = 0,
)

@Entity(tableName = "workout_lap", primaryKeys = ["startMs", "sport", "idx"])
data class WorkoutLapRow(
    val startMs: Long,
    val sport: Int,
    val idx: Int,
    val lapSec: Int? = null,
    val manual: Boolean = false,
    val distance: Double? = null,
    val pace: Int? = null,
    val speed: Double? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val ascent: Int? = null,
    val descent: Int? = null,
    val cadence: Int? = null,
)

@Entity(tableName = "workout_sample", primaryKeys = ["startMs", "sport", "idx"])
data class WorkoutSampleRow(
    val startMs: Long,
    val sport: Int,
    val idx: Int,
    val elapsedSec: Int? = null,
    val paused: Boolean = false,
    val pace: Int? = null,
    val hr: Int? = null,
    val altitude: Int? = null,
    val cadence: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Double? = null,
    val totalDistance: Double? = null,
)

/** A workout with its laps and samples, as one decoded transfer adds it. */
class WorkoutBatch(val workout: WorkoutRow, val laps: List<WorkoutLapRow>, val samples: List<WorkoutSampleRow>)
