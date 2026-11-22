package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.ActivitySeconds
import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSamples
import org.avmedia.gshockapi.model.HeartRateSummary
import org.avmedia.gshockapi.model.SleepData
import org.avmedia.gshockapi.model.SleepNight
import org.avmedia.gshockapi.model.SleepStage
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockapi.model.StairsRecord
import org.avmedia.gshockapi.model.StepCounterData

/** Decoded transfers for the history tests. */
internal object HistoryTestData {
    val noon: LocalDateTime = LocalDateTime.of(2026, 11, 20, 12, 30)

    fun night(date: LocalDate = LocalDate.of(2026, 11, 20), score: Int? = 82, total: Int? = 420) = SleepNight(
        date = date, zoneQuarterHours = null, sleepScore = score, rechargeStatus = 3, rechargeLevel = 2,
        fellAsleep = date.atTime(0, 10), wokeUp = date.atTime(7, 10), totalSleepMinutes = total, deepMinutes = 90,
        lightMinutes = 240, remMinutes = 90, awakeMinutes = 12, heartRateNight = 52, heartRateBaseline = null,
        heartRateVariability = null, breathingRate = 14.5f, breathingBaseline = null,
    )

    /** Steps in hours 8 to 11 only (a partial transfer has fewer hours); [steps] is the day total. */
    fun transfer(
        steps: Int? = 5000,
        at: LocalDateTime = noon,
        hours: Map<Int, Int> = mapOf(8 to 800, 9 to 1200, 10 to 1500, 11 to 1500),
        night: SleepNight? = night(),
        stages: List<SleepStage> = listOf(
            SleepStage(LocalDateTime.of(2026, 11, 20, 0, 10), "light"), SleepStage(LocalDateTime.of(2026, 11, 20, 1, 0), "deep"),
            SleepStage(LocalDateTime.of(2026, 11, 20, 3, 0), "rem"),
        ),
    ) = StepCounterData(
        timestamp = at, hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = steps,
        hourlyByHour = List(24) { hours[it] },
        h5600 = H5600HealthData(
            energyKcal = 310, heartRate = HeartRateSummary(maxBpm = 121, minBpm = 54, restingBpm = 58),
            recentHeartRate = HeartRateSamples(count = 5, minBpm = 70, maxBpm = 80, averageBpm = 75),
            activity = ActivitySeconds(runSeconds = 120, walkSeconds = 3600, cycleSeconds = 0, zeroSeconds = 0, otherSeconds = 0, stillSeconds = 7200, activeSeconds = 3720),
            stepGoal = 8000,
            sleep = night?.let { SleepData(night = it, nights = listOf(it), stages = stages, stagesStart = null) },
            body = BodyReadings(
                spo2 = listOf(Spo2Reading(at.minusHours(5), 96), Spo2Reading(at.minusHours(1), 97)),
                stairs = listOf(StairsRecord(at.minusHours(2), up = 4, down = 3)),
            ),
        ),
    )
}
