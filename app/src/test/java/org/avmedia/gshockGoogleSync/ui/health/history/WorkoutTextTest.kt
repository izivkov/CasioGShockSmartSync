package org.avmedia.gshockGoogleSync.ui.health.history

import org.avmedia.gshockGoogleSync.history.WorkoutMapper
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.history.WorkoutSampleRow
import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTextTest {
    /** One note under the rows instead of "(unconfirmed)" on every row. */
    @Test fun noRowSaysUnconfirmedAndOneNoteSaysSomeUnitsAreNotConfirmed() {
        val w = WorkoutMapper.map(WorkoutTestData.session(), 1L).workout
        assertTrue(WorkoutText.lines(w).none { it.second.contains("unconfirmed") || it.second.contains("unknown") })
        assertEquals("5:43 /km", WorkoutText.pace(343))
        assertEquals("5.25 km", WorkoutText.lines(w).first { it.first == "Distance" }.second)
        assertEquals("Some units are not confirmed yet.", WorkoutText.unitsNote(w))
    }

    /** A 25 s walk without GPS: no distance, so no distance, pace, speed, ascent, descent or cadence row and no note. */
    @Test fun aShortWorkoutWithoutDistanceShowsNoZeroRows() {
        val w = WorkoutRow(
            1, 9, "Walking", totalSec = 25, distance = 0.0, avgPace = 0, avgSpeed = 0.0, hrAvg = 125, hrMax = 131, hrMin = 123,
            ascent = 0, descent = 0, cadenceAvg = 0,
        )
        assertEquals(listOf("Duration", "Average heart rate", "Highest heart rate", "Lowest heart rate"), WorkoutText.lines(w).map { it.first })
        assertNull(WorkoutText.unitsNote(w))
    }

    @Test fun thePaceChartNeedsADistanceAndNeverHasZeroPaces() {
        val samples = List(5) { WorkoutSampleRow(1, 1, it, elapsedSec = it * 5, pace = 0, hr = 120) }
        val noDistance = WorkoutDetail(WorkoutRow(1, 9, "Walking", totalSec = 25, distance = 0.0), emptyList(), samples)
        assertTrue(WorkoutText.paceSeries(noDistance).isEmpty())
        val run = WorkoutMapper.map(WorkoutTestData.session(samples = 6), 1L)
        val withZero = run.samples.mapIndexed { i, s -> if (i == 0) s.copy(pace = 0) else s }
        val series = WorkoutText.paceSeries(WorkoutDetail(run.workout, run.laps, withZero))
        assertNull(series[0])
        assertTrue(series.drop(1).all { it != null && it > 0f })
    }

    @Test fun durationAndGaps() {
        assertEquals("30:00", WorkoutText.duration(1800))
        assertEquals("1:05:09", WorkoutText.duration(3909))
        assertEquals("no data", WorkoutText.duration(null))
        assertEquals("no summary", WorkoutText.line(WorkoutRow(1, 1, "Running")))
    }

    @Test fun seriesIsBoundedAndKeepsGaps() {
        val samples = List(1000) { WorkoutSampleRow(1, 1, it, hr = if (it % 20 == 0) 100 else null) }
        val s = WorkoutText.series(samples, 100) { it.hr }
        assertEquals(100, s.size)
        assertEquals(100f, s[0])
        assertNull(s[1])
        assertTrue(WorkoutText.series(emptyList()) { it.hr }.isEmpty())
    }

    @Test fun theListLineHasEnergyAndBothHeartRates() {
        val line = WorkoutText.line(WorkoutMapper.map(WorkoutTestData.session(), 1L).workout)
        assertTrue(line, line.contains("410 kcal"))
        assertTrue(line, line.contains("avg 142 bpm"))
        assertTrue(line, line.contains("max 171 bpm"))
    }

    @Test fun theLastWorkoutLineHasDurationEnergyAndBothHeartRatesOnly() {
        assertEquals("30:00, 410 kcal, avg 142 bpm, max 171 bpm", WorkoutText.lastLine(WorkoutMapper.map(WorkoutTestData.session(), 1L).workout))
        assertEquals("30:00, max 171 bpm", WorkoutText.lastLine(WorkoutMapper.map(WorkoutTestData.session(kcal = null, hrAvg = null), 1L).workout))
    }
}
