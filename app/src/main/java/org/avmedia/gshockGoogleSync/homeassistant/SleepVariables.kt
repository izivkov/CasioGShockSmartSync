package org.avmedia.gshockGoogleSync.homeassistant

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.avmedia.gshockapi.model.StepCounterData

/**
 * `health.*` variables from the sleep and night sections of the DW-H5600 LifeLog (0x53, 0x5A) and the
 * blood oxygen, breathing, cardio and stairs sections. Part of [HealthVariables]; null when the last
 * transfer held no value, never 0.
 */
object SleepVariables {
    private const val UNCONFIRMED_TIME =
        "UNCONFIRMED: the time base of the sleep record is read as the watch's wall clock on the record's date, not yet checked on a real night"
    private const val UNCONFIRMED_STAGES =
        "UNCONFIRMED: stage times are read as seconds from the start time of the record, not yet checked on a real night"
    private const val MAX_STAGES = 200

    val DESCRIPTIONS: Map<String, String> = linkedMapOf(
        "health.sleep_date" to "Date of the last night that has data, ISO 8601 date ($UNCONFIRMED_TIME)",
        "health.sleep_score" to "Sleep score of the last night, 1 to 100",
        "health.recharge_status" to "Nightly Recharge status of the last night, 1 to 5",
        "health.recharge_level" to "Nightly Recharge level of the last night",
        "health.sleep_fell_asleep" to "When you fell asleep, watch local time, ISO 8601 without offset ($UNCONFIRMED_TIME)",
        "health.sleep_woke_up" to "When you woke up, watch local time, ISO 8601 without offset ($UNCONFIRMED_TIME)",
        "health.sleep_total_min" to "Total sleep of the last night, minutes",
        "health.sleep_deep_min" to "Deep sleep of the last night, minutes",
        "health.sleep_light_min" to "Light sleep of the last night, minutes",
        "health.sleep_rem_min" to "REM sleep of the last night, minutes",
        "health.sleep_awake_min" to "Long interruptions of the last night, minutes",
        "health.sleep_hr" to "Heart rate during the last night as the record gives it, bpm",
        "health.sleep_breathing" to "Breathing rate during the last night as the record gives it, breaths per minute",
        "health.sleep_stages" to "Sleep stages of the last night as a list of time (ISO 8601) and stage (awake, rem, light, deep) ($UNCONFIRMED_STAGES)",
        "health.spo2" to "Latest blood oxygen reading, percent",
        "health.spo2_at" to "When the latest blood oxygen reading was taken, watch local time, ISO 8601 without offset",
        "health.breathing_exercise_at" to "Start of the latest breathing exercise, watch local time, ISO 8601 without offset",
        "health.cardio_load_ratio" to "Cardio load ratio of the latest cardio status (0 to 9.99): today's current value when the watch has one, else the newest daily record",
        "health.cardio_date" to "Date of the cardio status in health.cardio_*, ISO 8601 date",
        "health.cardio_feedback" to "Cardio status feedback of the latest cardio status, 1 to 4",
        "health.cardio_strain" to "Strain of the latest cardio status, 0 to 999",
        "health.cardio_tolerance" to "Tolerance of the latest cardio status, 0 to 999",
        "health.stairs_up" to "Floors up of the latest stairs record (UNCONFIRMED: the two counts are read as up and down)",
        "health.stairs_down" to "Floors down of the latest stairs record (UNCONFIRMED: the two counts are read as up and down)",
    )

    private fun iso(time: LocalDateTime?): String? = time?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun values(data: StepCounterData?): Map<String, Any?> {
        val h = data?.h5600
        val night = h?.sleep?.night
        val stages = h?.sleep?.stages.orEmpty().takeLast(MAX_STAGES)
            .map { mapOf("time" to iso(it.time), "stage" to it.stage) }
            .takeIf { it.isNotEmpty() }
        val body = h?.body
        val spo2 = body?.spo2?.lastOrNull()
        // Today's value from the current values (0x65) when the watch has one, else the newest daily record (0x5B).
        val cardio = body?.cardio ?: body?.cardioNow
        val stairs = body?.stairs?.lastOrNull()
        return linkedMapOf(
            "health.sleep_date" to night?.date?.toString(),
            "health.sleep_score" to night?.sleepScore,
            "health.recharge_status" to night?.rechargeStatus,
            "health.recharge_level" to night?.rechargeLevel,
            "health.sleep_fell_asleep" to iso(night?.fellAsleep),
            "health.sleep_woke_up" to iso(night?.wokeUp),
            "health.sleep_total_min" to night?.totalSleepMinutes,
            "health.sleep_deep_min" to night?.deepMinutes,
            "health.sleep_light_min" to night?.lightMinutes,
            "health.sleep_rem_min" to night?.remMinutes,
            "health.sleep_awake_min" to night?.awakeMinutes,
            "health.sleep_hr" to night?.heartRateNight,
            "health.sleep_breathing" to night?.breathingRate,
            "health.sleep_stages" to stages,
            "health.spo2" to spo2?.percent,
            "health.spo2_at" to iso(spo2?.time),
            "health.breathing_exercise_at" to iso(body?.breathingSessions?.lastOrNull()?.time),
            "health.cardio_load_ratio" to cardio?.loadRatio,
            "health.cardio_date" to cardio?.date?.toString(),
            "health.cardio_feedback" to cardio?.feedback,
            "health.cardio_strain" to cardio?.strain,
            "health.cardio_tolerance" to cardio?.tolerance,
            "health.stairs_up" to stairs?.up,
            "health.stairs_down" to stairs?.down,
        )
    }
}
