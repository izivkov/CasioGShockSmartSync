package org.avmedia.gshockGoogleSync.history

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.avmedia.gshockGoogleSync.ui.health.resolveDistanceKm
import org.avmedia.gshockapi.model.SleepNight
import org.avmedia.gshockapi.model.StepCounterData

/** Turns one decoded transfer into history rows. Only reads the decoded data: nothing is asked of the watch. */
object HistoryMapper {
    private fun iso(t: LocalDateTime?): String? = t?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun map(data: StepCounterData?): HistoryBatch {
        val h = data?.h5600 ?: return HistoryBatch(emptyList(), emptyList(), emptyList())
        val ts = data.timestamp
        val today: LocalDate? = ts?.toLocalDate()
        val days = ArrayList<DayRow>()
        val hours = LinkedHashMap<Int, HourRow>()

        if (ts != null && today != null) {
            val hr = h.heartRate
            val act = h.activity
            days += DayRow(
                date = today.toString(),
                steps = data.currentDaySteps,
                distanceM = data.takeIf { it.currentDaySteps != null }?.let { Math.round(resolveDistanceKm(it) * 1000f) },
                kcal = h.energyKcal,
                activeMin = act?.activeSeconds?.div(60),
                walkSec = act?.walkSeconds, runSec = act?.runSeconds, stillSec = act?.stillSeconds,
                hrRest = hr?.restingBpm, hrMin = hr?.minBpm, hrMax = hr?.maxBpm,
            )
            data.hourlyByHour.forEachIndexed { i, steps -> if (steps != null) hours[i] = HourRow(today.toString(), i, steps = steps) }
            val recentHr = h.recentHeartRate?.averageBpm
            val hour = ts.hour
            if (recentHr != null) hours[hour] = (hours[hour] ?: HourRow(today.toString(), hour)).copy(hr = recentHr)
        }
        // Blood oxygen and stairs carry their own times: each goes to its own day, the latest reading of the day.
        h.body?.spo2.orEmpty().groupBy { it.time.toLocalDate() }
            .forEach { (d, list) -> days += DayRow(d.toString(), spo2 = list.maxByOrNull { it.time }?.percent) }
        h.body?.stairs.orEmpty().groupBy { it.time.toLocalDate() }
            .forEach { (d, list) -> list.maxByOrNull { it.time }?.let { days += DayRow(d.toString(), stairsUp = it.up, stairsDown = it.down) } }

        val sleep = h.sleep
        val stages = sleep?.stages.orEmpty().joinToString("\n") { "${iso(it.time)},${it.stage}" }.takeIf { it.isNotEmpty() }
        val nights = (sleep?.nights.orEmpty() + listOfNotNull(sleep?.night))
            .filter { !it.isEmpty }
            .map { n -> night(n, stages.takeIf { n.date == sleep?.night?.date }) }

        return HistoryBatch(days.groupBy { it.date }.values.map { rs -> rs.reduce { old, new -> new.over(old) } }, hours.values.toList(), nights.groupBy { it.date }.values.map { rs -> rs.reduce { old, new -> new.over(old) } })
    }

    private fun night(n: SleepNight, stages: String?) = NightRow(
        date = n.date.toString(), score = n.sleepScore, rechargeStatus = n.rechargeStatus, rechargeLevel = n.rechargeLevel,
        fellAsleep = iso(n.fellAsleep), wokeUp = iso(n.wokeUp), totalMin = n.totalSleepMinutes, deepMin = n.deepMinutes,
        lightMin = n.lightMinutes, remMin = n.remMinutes, awakeMin = n.awakeMinutes, hr = n.heartRateNight,
        breathing = n.breathingRate, stages = stages,
    )
}
