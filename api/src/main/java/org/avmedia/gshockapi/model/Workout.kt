package org.avmedia.gshockapi.model

import androidx.annotation.Keep

/** One workout downloaded from a DW-H5600. Every value the watch marks "no data" is null. */
@Keep
data class WorkoutSession(
    /** Slot in the watch's group header (0 to 99). */
    val slot: Int,
    /** Sport code as the watch stores it (1 to 14), see [WorkoutSports]. */
    val sport: Int,
    val sportName: String,
    /** Start and end, UTC, epoch millis; null when the BCD time is invalid. */
    val startUtcMillis: Long?,
    val endUtcMillis: Long?,
    /** Raw i8 from header offset 32 (likely quarter hours; unconfirmed). */
    val utcOffsetRaw: Int,
    val hasGps: Boolean,
    val formatVersion: Int,
    val summary: WorkoutSummary,
    val laps: List<WorkoutLap>,
    val samples: List<WorkoutSample>,
    /** Fields whose unit is likely or unknown, name to description. Show and send these with "unconfirmed". */
    val unconfirmed: Map<String, String> = WorkoutUnits.UNCONFIRMED,
    /** Things that did not decode cleanly (cut records, unknown sport, a block chain that ended early). */
    val warnings: List<String> = emptyList(),
)

@Keep
data class WorkoutSummary(
    val totalSeconds: Int? = null,
    /** Likely km (unconfirmed). */
    val distance: Double? = null,
    /** Seconds per km, likely (unconfirmed). */
    val avgPace: Int? = null,
    val fastestPace: Int? = null,
    val slowestPace: Int? = null,
    /** Likely km/h (unconfirmed). */
    val avgSpeed: Double? = null,
    val maxSpeed: Double? = null,
    val kcal: Long? = null,
    /** Share of carbohydrate, protein, fat. */
    val fuel: List<Int?>? = null,
    val cardioLoad: Int? = null,
    /** 0 none, 1 very low, 2 low, 3 medium, 4 high, 5 very high. */
    val loadFeedback: Int? = null,
    val runningIndex: Int? = null,
    val runningRating: Int? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val altHigh: Int? = null,
    val altLow: Int? = null,
    val ascent: Long? = null,
    val descent: Long? = null,
    val cadenceAvg: Int? = null,
    val cadenceMax: Int? = null,
    /** Likely cm (unconfirmed). */
    val strideAvg: Int? = null,
    val verticalAvg: Int? = null,
    val lapCount: Int? = null,
    val cardioLoadFloat: Double? = null,
    /** Swim extras (strokes, SWOLF) by name; empty for other sports. */
    val swim: Map<String, Long> = emptyMap(),
    /** Profile values stored with the workout (age, height, weight in 0.1 kg, ...) by name. */
    val profile: Map<String, Int> = emptyMap(),
)

@Keep
data class WorkoutLap(
    val index: Int,
    /** Time of day at the end of the lap, seconds from the head (u8 hours, BCD minutes and seconds). */
    val atSeconds: Int?,
    val lapSeconds: Int?,
    /** 0xFE in the manual flag means not set (likely automatic). */
    val manual: Boolean,
    val distance: Double? = null,
    val pace: Int? = null,
    val speed: Double? = null,
    val hrAvg: Int? = null,
    val hrMax: Int? = null,
    val hrMin: Int? = null,
    val ascent: Int? = null,
    val descent: Int? = null,
    val cadence: Int? = null,
    val stride: Int? = null,
    val totalDistance: Double? = null,
    /** Everything else of the record by name (running form, strokes, SWOLF); units unknown. */
    val extras: Map<String, Double> = emptyMap(),
)

@Keep
data class WorkoutSample(
    val elapsedSeconds: Int?,
    /** Likely paused when 1 (unconfirmed). */
    val paused: Boolean,
    val pace: Int? = null,
    val heartRate: Int? = null,
    val hrReliability: Int? = null,
    val altitude: Int? = null,
    val cadence: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Double? = null,
    val totalDistance: Double? = null,
    val stride: Int? = null,
    val extras: Map<String, Double> = emptyMap(),
)

object WorkoutSports {
    val NAMES: Map<Int, String> = mapOf(
        1 to "Running", 2 to "Treadmill running", 3 to "Motion sensor running", 4 to "Trail running", 5 to "Walking",
        6 to "Treadmill walking", 7 to "Motion sensor walking", 8 to "Biking", 9 to "Pool swimming",
        10 to "Open water swimming", 11 to "Gym workout", 12 to "Trekking", 13 to "Other", 14 to "Interval timer",
    )
    fun name(code: Int): String = NAMES[code] ?: "Sport $code"
}

object WorkoutUnits {
    /** Units or meanings that are likely or unknown, not proven. Keep "unconfirmed" in every place they are shown or sent. */
    val UNCONFIRMED: Map<String, String> = mapOf(
        "distance" to "likely km, unconfirmed",
        "pace" to "likely seconds per km, unconfirmed",
        "speed" to "likely km/h, unconfirmed",
        "stride" to "likely cm, unconfirmed",
        "cadence" to "unit unknown, unconfirmed",
        "running form" to "units unknown, unconfirmed",
        "utcOffset" to "likely quarter hours, unconfirmed",
        "paused" to "1 is likely paused, unconfirmed",
        "treadmill and motion sensor sports" to "decoded like running or walking, unconfirmed",
    )
}
