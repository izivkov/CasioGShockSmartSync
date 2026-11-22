package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.model.WorkoutSession

/** Turns one decoded workout into history rows. */
object WorkoutMapper {
    /** The key time: the start; when the watch's start time was invalid the end; else a negative number from the slot. */
    fun keyMs(s: WorkoutSession): Long = s.startUtcMillis ?: s.endUtcMillis ?: -(1L + s.slot)

    fun map(s: WorkoutSession, nowMs: Long = System.currentTimeMillis()): WorkoutBatch {
        val key = keyMs(s)
        val m = s.summary
        val row = WorkoutRow(
            startMs = key, sport = s.sport, sportName = s.sportName, endMs = s.endUtcMillis, utcOffsetRaw = s.utcOffsetRaw,
            hasGps = s.hasGps, totalSec = m.totalSeconds, distance = m.distance, avgPace = m.avgPace, avgSpeed = m.avgSpeed,
            kcal = m.kcal, hrAvg = m.hrAvg, hrMax = m.hrMax, hrMin = m.hrMin, ascent = m.ascent, descent = m.descent,
            cadenceAvg = m.cadenceAvg, lapCount = m.lapCount ?: s.laps.size,
            warnings = s.warnings.joinToString("\n").ifEmpty { null }, savedAtMs = nowMs,
        )
        val laps = s.laps.map { l ->
            WorkoutLapRow(
                key, s.sport, l.index, l.lapSeconds, l.manual, l.distance, l.pace, l.speed, l.hrMax, l.hrMax, l.hrMin,
                l.ascent, l.descent, l.cadence,
            )
        }
        val samples = s.samples.mapIndexed { i, p ->
            WorkoutSampleRow(
                key, s.sport, i, p.elapsedSeconds, p.paused, p.pace, p.heartRate, p.altitude, p.cadence, p.latitude,
                p.longitude, p.speed, p.totalDistance,
            )
        }
        return WorkoutBatch(row, laps, samples)
    }
}
