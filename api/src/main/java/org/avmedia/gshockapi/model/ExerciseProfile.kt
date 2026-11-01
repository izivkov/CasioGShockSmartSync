package org.avmedia.gshockapi.model

import androidx.annotation.Keep

@Keep
data class ExerciseProfile(
    val stepGoal: Int = 10000,
    val height: Int = 0,
    val weight: Int = 0,
) {
    val step_goal: Int
        get() = stepGoal

    val heightCm: Int
        get() = height

    val weightKg: Int
        get() = weight
}
