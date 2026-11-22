package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.model.WorkoutLap
import org.avmedia.gshockapi.model.WorkoutSample
import org.avmedia.gshockapi.model.WorkoutSession
import org.avmedia.gshockapi.model.WorkoutSummary

/** A decoded workout shaped like what the api returns, for the app tests. */
internal object WorkoutTestData {
    const val START = 1_763_800_000_000L

    fun session(
        start: Long? = START, sport: Int = 1, laps: Int = 2, samples: Int = 4, kcal: Long? = 410, hrAvg: Int? = 142, distance: Double? = 5.25,
    ) = WorkoutSession(
        slot = 3, sport = sport, sportName = org.avmedia.gshockapi.model.WorkoutSports.name(sport), startUtcMillis = start,
        endUtcMillis = start?.plus(1800_000), utcOffsetRaw = 4, hasGps = false, formatVersion = 1,
        summary = WorkoutSummary(totalSeconds = 1800, distance = distance, avgPace = 343, kcal = kcal, hrAvg = hrAvg, hrMax = 171, hrMin = 98, lapCount = laps),
        laps = List(laps) { WorkoutLap(index = it, atSeconds = 600 * (it + 1), lapSeconds = 600, manual = it == 0, distance = 1.75, pace = 340, hrAvg = 140 + it, hrMax = 160) },
        samples = List(samples) { WorkoutSample(elapsedSeconds = it * 10, paused = false, pace = 330 + it, heartRate = if (it == 2) null else 120 + it) },
    )
}
