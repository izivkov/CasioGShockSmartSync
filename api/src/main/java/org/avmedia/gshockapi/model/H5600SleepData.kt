package org.avmedia.gshockapi.model

import androidx.annotation.Keep
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Sleep and night values from the LifeLog sections 0x53 (stage timeline) and 0x5A (nightly record).
 *
 * Everything the watch marks "no data" is null. Times are the watch's wall clock without a zone.
 * UNCONFIRMED until a real night is checked: the time base of the stage timeline (read as seconds from
 * its start time) and the date a night belongs to (read as the day it began).
 */
@Keep
data class SleepData(
    /** The newest night that has any value, or null. */
    val night: SleepNight?,
    /** All nights of the transfer, oldest first. */
    val nights: List<SleepNight>,
    /** Stage changes of the newest timeline record, oldest first. */
    val stages: List<SleepStage>,
    /** Start of the newest timeline record. */
    val stagesStart: LocalDateTime?,
)

@Keep
data class SleepNight(
    val date: LocalDate,
    /** Time zone byte of the record in quarter hours (UTC-4 is -16). */
    val zoneQuarterHours: Int?,
    val sleepScore: Int?,
    val rechargeStatus: Int?,
    val rechargeLevel: Int?,
    val fellAsleep: LocalDateTime?,
    val wokeUp: LocalDateTime?,
    val totalSleepMinutes: Int?,
    val deepMinutes: Int?,
    val lightMinutes: Int?,
    val remMinutes: Int?,
    /** Long interruptions during the night, minutes. */
    val awakeMinutes: Int?,
    val heartRateNight: Int?,
    val heartRateBaseline: Int?,
    val heartRateVariability: Int?,
    val breathingRate: Float?,
    val breathingBaseline: Float?,
) {
    /** True when the record holds nothing but "no data". */
    val isEmpty: Boolean
        get() =
            sleepScore == null && rechargeStatus == null && rechargeLevel == null && fellAsleep == null &&
                wokeUp == null && totalSleepMinutes == null && deepMinutes == null && lightMinutes == null &&
                remMinutes == null && heartRateNight == null && breathingRate == null
}

/** One change of sleep stage: from [time] on the sleeper is in [stage] ("awake", "rem", "light", "deep"). */
@Keep
data class SleepStage(
    val time: LocalDateTime,
    val stage: String,
)

/** Other LifeLog sections that come as plain record lists. All fields null when the watch has no value. */
@Keep
data class BodyReadings(
    val spo2: List<Spo2Reading> = emptyList(),
    val breathingSessions: List<BreathingSession> = emptyList(),
    val cardio: CardioStatus? = null,
    val stairs: List<StairsRecord> = emptyList(),
    /** Cardio load of today from the current values (0x65, group 0x5B), dated with the read; null when the watch has none. */
    val cardioNow: CardioStatus? = null,
)

@Keep
data class Spo2Reading(val time: LocalDateTime, val percent: Int)

/** UNCONFIRMED: only the time of a breathing exercise is read; its eight duration digits are kept as they come. */
@Keep
data class BreathingSession(val time: LocalDateTime, val durationDigits: List<Int?>)

/** Newest valid cardio status record. */
@Keep
data class CardioStatus(
    val date: LocalDate,
    val loadRatio: Float?,
    val feedback: Int?,
    val strain: Int?,
    val tolerance: Int?,
)

/** UNCONFIRMED: the two counts are read as floors up and floors down. */
@Keep
data class StairsRecord(val time: LocalDateTime, val up: Int, val down: Int)
