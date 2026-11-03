package org.avmedia.gshockapi.model

import androidx.annotation.Keep

/**
 * Unit system the user entered height and weight in.
 * The watch always stores metric values (cm and kg).
 */
@Keep
enum class ExerciseUnits {
    METRIC,
    IMPERIAL,
}

/**
 * Exercise profile as the user entered it.
 *
 * [height] is in cm when [units] is METRIC, or in inches when IMPERIAL.
 * [weight] is in kg when [units] is METRIC, or in pounds when IMPERIAL.
 * Convert to metric only when reading or writing the watch.
 */
@Keep
data class ExerciseProfile(
    val stepGoal: Int = 10000,
    val height: Int = 170,
    val weight: Int = 70,
    val units: ExerciseUnits = ExerciseUnits.METRIC,
) {
    /** Snake-case alias for [stepGoal]. Kept for existing callers. */
    val step_goal: Int
        get() = stepGoal

    /** Metric height for the watch. Only correct when [units] is METRIC; use [toWatchHeightCm] otherwise. */
    val heightCm: Int
        get() = height

    /** Metric weight for the watch. Only correct when [units] is METRIC; use [toWatchWeightKg] otherwise. */
    val weightKg: Int
        get() = weight

    /** Height converted to whole centimetres for the watch, regardless of [units]. */
    val toWatchHeightCm: Int
        get() = if (units == ExerciseUnits.IMPERIAL) Math.round(height * 2.54).toInt() else height

    /** Weight converted to whole kilograms for the watch, regardless of [units]. */
    val toWatchWeightKg: Int
        get() = if (units == ExerciseUnits.IMPERIAL) Math.round(weight * 0.45359237).toInt() else weight
}
