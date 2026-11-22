package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockGoogleSync.ui.health.resolveDistanceKm
import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSamples
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.StepCounterData

/**
 * Maps a decoded DW-H5600 LifeLog ([StepCounterData] with `h5600`) to the `health.*` variable values.
 * Values come from the last LifeLog transfer (see H5600Session); nothing here talks to the watch.
 */
object HealthVariables {
    private const val UNCONFIRMED_KCAL =
        "UNCONFIRMED: the watch's header energy read as kcal; it matched the Casio app within 1 kcal on two days, the scale is not verified (upstream issue 155)"
    private const val UNCONFIRMED_STEPS = "header step total, not verified against the app on every day"

    /** name to description, in display order. */
    val DESCRIPTIONS: Map<String, String> = linkedMapOf(
        "health.steps_today" to "Steps today ($UNCONFIRMED_STEPS)",
        "health.recorded_at" to "When the LifeLog was recorded, watch local time, ISO 8601 without offset",
        "health.kcal_today" to "Energy today, kcal ($UNCONFIRMED_KCAL)",
        "health.hr_rest" to "Resting heart rate of the day, bpm",
        "health.hr_min" to "Lowest heart rate of the day, bpm",
        "health.hr_max" to "Highest heart rate of the day, bpm",
        "health.hr_recent_avg" to "Average heart rate of the last few minutes of the transfer, bpm (the latest single sample is not available)",
        "health.hr_recent_min" to "Lowest heart rate of the last few minutes of the transfer, bpm",
        "health.hr_recent_max" to "Highest heart rate of the last few minutes of the transfer, bpm",
        "health.active_seconds" to "Active time today (walk + cycle + other), seconds",
        "health.active_minutes" to "Active time today, whole minutes",
        "health.walk_seconds" to "Walking time today, seconds",
        "health.run_seconds" to "Running time today, seconds",
        "health.cycle_seconds" to "Cycling time today, seconds",
        "health.other_seconds" to "Other activity time today, seconds",
        "health.still_seconds" to "Time without activity today, seconds",
        "health.distance_m" to "Distance today, metres (estimate: the watch sends none; steps times 0.73 m, as on the Health screen)",
        "health.step_goal" to "Daily step goal set on the watch",
        "health.lifelog_pending" to "True when the watch still has LifeLog data it has not transferred",
        "health.slot_minutes" to "Length of one entry of health.slots, minutes",
        "health.slots" to "Steps per 5-minute slot, newest first (entry i ends i * 5 minutes before health.recorded_at); null = slot skipped as implausible",
        "health.steps_by_hour" to "Steps per hour of the day (24 entries, null = no data; partial when only recent slots were transferred)",
    ) + SleepVariables.DESCRIPTIONS

    val NAMES: List<String> = DESCRIPTIONS.keys.toList()

    fun values(data: StepCounterData?): Map<String, Any?> {
        val h = data?.h5600
        val hr = h?.heartRate
        val recent = h?.recentHeartRate
        val act = h?.activity
        val slots = data?.hourlyIntervals.orEmpty().let { intervals ->
            if (intervals.isEmpty()) null
            else {
                val byIndex = intervals.associate { it.index to it.steps }
                (0..intervals.maxOf { it.index }).map { byIndex[it] }
            }
        }
        return base(data, h, hr, recent, act, slots) + SleepVariables.values(data)
    }

    private fun base(
        data: StepCounterData?,
        h: H5600HealthData?,
        hr: HeartRateSummary?,
        recent: HeartRateSamples?,
        act: ActivitySeconds?,
        slots: List<Int?>?,
    ): Map<String, Any?> {
        return linkedMapOf(
            "health.steps_today" to data?.currentDaySteps,
            "health.recorded_at" to data?.timestamp?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "health.kcal_today" to h?.energyKcal,
            "health.hr_rest" to hr?.restingBpm,
            "health.hr_min" to hr?.minBpm,
            "health.hr_max" to hr?.maxBpm,
            "health.hr_recent_avg" to recent?.averageBpm,
            "health.hr_recent_min" to recent?.minBpm,
            "health.hr_recent_max" to recent?.maxBpm,
            "health.active_seconds" to act?.activeSeconds,
            "health.active_minutes" to act?.activeSeconds?.div(60),
            "health.walk_seconds" to act?.walkSeconds,
            "health.run_seconds" to act?.runSeconds,
            "health.cycle_seconds" to act?.cycleSeconds,
            "health.other_seconds" to act?.otherSeconds,
            "health.still_seconds" to act?.stillSeconds,
            "health.distance_m" to data?.takeIf { it.currentDaySteps != null }
                ?.let { Math.round(resolveDistanceKm(it) * 1000f) },
            "health.step_goal" to h?.stepGoal,
            "health.lifelog_pending" to h?.lifeLogPending,
            "health.slot_minutes" to if (slots != null) 5 else null,
            "health.slots" to slots,
            "health.steps_by_hour" to data?.hourlyByHour?.takeIf { it.isNotEmpty() },
        )
    }
}
