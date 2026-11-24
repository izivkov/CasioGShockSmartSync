package org.avmedia.gshockGoogleSync.watchvalues

import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.DataUse.BLOOD_OXYGEN
import org.avmedia.smartsync.plugin.api.DataUse.BODY_PROFILE
import org.avmedia.smartsync.plugin.api.DataUse.HEART_RATE
import org.avmedia.smartsync.plugin.api.DataUse.SLEEP
import org.avmedia.smartsync.plugin.api.DataUse.STEPS
import org.avmedia.smartsync.plugin.api.DataUse.WATCH_SETTINGS
import org.avmedia.smartsync.plugin.api.DataUse.WORKOUTS
import org.avmedia.smartsync.plugin.api.ValueType
import org.avmedia.smartsync.plugin.api.ValueType.BOOL
import org.avmedia.smartsync.plugin.api.ValueType.DATE
import org.avmedia.smartsync.plugin.api.ValueType.DATE_TIME
import org.avmedia.smartsync.plugin.api.ValueType.LIST
import org.avmedia.smartsync.plugin.api.ValueType.NUMBER
import org.avmedia.smartsync.plugin.api.ValueType.TEXT
import org.avmedia.smartsync.plugin.api.ValueType.TIME
import org.avmedia.smartsync.plugin.api.ValueType.WHOLE

/**
 * What kind of data a variable is ([DataUse]), its type and unit: the plugin catalogue
 * ([org.avmedia.smartsync.plugin.api.ValueInfo]) and the access guard read it from the [Variable]. Stated per
 * variable; a variable without one is served to no plugin.
 */
data class ValueMeta(val use: DataUse, val type: ValueType, val unit: String? = null) {
    companion object {
        fun of(use: DataUse, type: ValueType, unit: String? = null) = ValueMeta(use, type, unit)

        /** The raw codes that hold the body profile (0x45 profile, 0x4F heart rate limits and VO2max); the others are settings. */
        private val BODY_CODES = setOf(0x45, 0x4F)

        fun ofRawCode(code: Int): ValueMeta = ValueMeta(if (code in BODY_CODES) BODY_PROFILE else WATCH_SETTINGS, TEXT)

        /** The `health.*` variables ([HealthVariables], [HeartRateDayVariables], [SleepVariables]), one row each. */
        val HEALTH: Map<String, ValueMeta> = mapOf(
            "health.steps_today" to of(STEPS, WHOLE, "steps"),
            "health.recorded_at" to of(STEPS, DATE_TIME),
            "health.kcal_today" to of(STEPS, WHOLE, "kcal"),
            "health.energy_total_kcal" to of(STEPS, WHOLE, "kcal"),
            "health.energy_resting_kcal" to of(STEPS, WHOLE, "kcal"),
            "health.energy_active_kcal" to of(STEPS, WHOLE, "kcal"),
            "health.hr_rest" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_min" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_max" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_recent_avg" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_recent_min" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_recent_max" to of(HEART_RATE, WHOLE, "bpm"),
            "health.active_seconds" to of(STEPS, WHOLE, "s"),
            "health.active_minutes" to of(STEPS, WHOLE, "min"),
            "health.walk_seconds" to of(STEPS, WHOLE, "s"),
            "health.run_seconds" to of(STEPS, WHOLE, "s"),
            "health.cycle_seconds" to of(STEPS, WHOLE, "s"),
            "health.other_seconds" to of(STEPS, WHOLE, "s"),
            "health.still_seconds" to of(STEPS, WHOLE, "s"),
            "health.distance_m" to of(STEPS, WHOLE, "m"),
            "health.step_goal" to of(STEPS, WHOLE, "steps"),
            "health.lifelog_pending" to of(STEPS, BOOL),
            "health.slot_minutes" to of(STEPS, WHOLE, "min"),
            "health.slots" to of(STEPS, LIST, "steps"),
            "health.steps_by_hour" to of(STEPS, LIST, "steps"),
            "health.hr_by_slot" to of(HEART_RATE, LIST, "bpm"),
            "health.hr_max_at" to of(HEART_RATE, DATE_TIME),
            "health.hr_min_at" to of(HEART_RATE, DATE_TIME),
            "health.hr_curve_max" to of(HEART_RATE, WHOLE, "bpm"),
            "health.hr_curve_min" to of(HEART_RATE, WHOLE, "bpm"),
            "health.sleep_date" to of(SLEEP, DATE),
            "health.sleep_score" to of(SLEEP, WHOLE),
            "health.recharge_status" to of(SLEEP, WHOLE),
            "health.recharge_level" to of(SLEEP, WHOLE),
            "health.sleep_fell_asleep" to of(SLEEP, DATE_TIME),
            "health.sleep_woke_up" to of(SLEEP, DATE_TIME),
            "health.sleep_total_min" to of(SLEEP, WHOLE, "min"),
            "health.sleep_deep_min" to of(SLEEP, WHOLE, "min"),
            "health.sleep_light_min" to of(SLEEP, WHOLE, "min"),
            "health.sleep_rem_min" to of(SLEEP, WHOLE, "min"),
            "health.sleep_awake_min" to of(SLEEP, WHOLE, "min"),
            "health.sleep_hr" to of(SLEEP, WHOLE, "bpm"),
            "health.sleep_breathing" to of(SLEEP, NUMBER, "breaths/min"),
            "health.sleep_stages" to of(SLEEP, LIST),
            "health.spo2" to of(BLOOD_OXYGEN, WHOLE, "%"),
            "health.spo2_at" to of(BLOOD_OXYGEN, DATE_TIME),
            "health.breathing_exercise_at" to of(SLEEP, DATE_TIME),
            "health.cardio_load_ratio" to of(HEART_RATE, NUMBER),
            "health.cardio_date" to of(HEART_RATE, DATE),
            "health.cardio_feedback" to of(HEART_RATE, WHOLE),
            "health.cardio_strain" to of(HEART_RATE, WHOLE),
            "health.cardio_tolerance" to of(HEART_RATE, WHOLE),
            "health.stairs_up" to of(STEPS, WHOLE, "floors"),
            "health.stairs_down" to of(STEPS, WHOLE, "floors"),
        )

        /** The `workout.*` variables ([WorkoutVariables]). */
        val WORKOUT: Map<String, ValueMeta> = mapOf(
            "workout.sport" to of(WORKOUTS, TEXT),
            "workout.start" to of(WORKOUTS, DATE_TIME),
            "workout.duration_seconds" to of(WORKOUTS, WHOLE, "s"),
            "workout.distance" to of(WORKOUTS, NUMBER),
            "workout.kcal" to of(WORKOUTS, WHOLE, "kcal"),
            "workout.hr_avg" to of(WORKOUTS, WHOLE, "bpm"),
            "workout.hr_max" to of(WORKOUTS, WHOLE, "bpm"),
            "workout.unconfirmed" to of(WORKOUTS, TEXT),
        )

        /** The body profile settings ([WatchSettingRegistry] keys): height, weight, sex, heart rate limits, VO2max. */
        val BODY_SETTINGS = setOf("profile_height_cm", "profile_weight_kg", "profile_sex", "max_heart_rate", "resting_heart_rate", "vo2max", "training_background")

        /** The meta of a decoded setting value of [key] ([WatchSettingRegistry]) of [type]. */
        fun ofSetting(key: String, type: SettingType): ValueMeta = ValueMeta(
            if (key in BODY_SETTINGS) BODY_PROFILE else WATCH_SETTINGS,
            when (type) {
                is SettingType.Choice -> TEXT
                is SettingType.Whole -> WHOLE
                SettingType.Bool -> BOOL
                SettingType.Time -> TIME
            },
            unitOf(key),
        )

        private fun unitOf(key: String): String? = when {
            key.endsWith("_cm") -> "cm"
            key.endsWith("_kg") -> "kg"
            key.endsWith("_min") -> "min"
            key.endsWith("heart_rate") -> "bpm"
            key == "step_goal" -> "steps"
            else -> null
        }
    }
}
