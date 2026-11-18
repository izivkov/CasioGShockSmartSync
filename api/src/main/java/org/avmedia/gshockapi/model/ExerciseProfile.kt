package org.avmedia.gshockapi.model

import androidx.annotation.Keep
import kotlin.math.roundToInt

private const val CM_PER_INCH = 2.54
private const val KG_PER_POUND = 0.45359237

/**
 * Metric is the source of truth, matching the watch: height in whole cm, weight in whole kg.
 * Imperial getters are exact. Only the withX functions round, because the watch stores whole units.
 */
@Keep
data class ExerciseProfile(
    val stepGoal: Int = 10000,
    val height: Int = 0, // cm
    val weight: Int = 0, // kg
) {
    val step_goal: Int
        get() = stepGoal

    val heightCm: Int
        get() = height

    val weightKg: Int
        get() = weight

    val heightInches: Double
        get() = height / CM_PER_INCH

    val weightPounds: Double
        get() = weight / KG_PER_POUND

    fun withHeightInches(inches: Double): ExerciseProfile = copy(height = (inches * CM_PER_INCH).roundToInt())

    fun withWeightPounds(pounds: Double): ExerciseProfile = copy(weight = (pounds * KG_PER_POUND).roundToInt())

    /** The pounds value the watch will actually hold after saving these pounds. */
    fun storedPoundsFor(pounds: Double): Double = (pounds * KG_PER_POUND).roundToInt() / KG_PER_POUND
}
