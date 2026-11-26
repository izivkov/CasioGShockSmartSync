package org.avmedia.gshockGoogleSync.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMapperTest {
    @Test fun summaryLapsAndSamplesAreCarriedOver() {
        val b = WorkoutMapper.map(WorkoutTestData.session(), 99L)
        assertEquals(WorkoutTestData.START, b.workout.startMs)
        assertEquals(1, b.workout.sport)
        assertEquals("Running", b.workout.sportName)
        assertEquals(WorkoutTestData.START + 1_800_000, b.workout.endMs)
        assertEquals(1800, b.workout.totalSec)
        assertEquals(410L, b.workout.kcal)
        assertEquals(142, b.workout.hrAvg)
        assertEquals(171, b.workout.hrMax)
        assertEquals(99L, b.workout.savedAtMs)
        assertEquals(listOf(0, 1), b.laps.map { it.idx })
        assertEquals(listOf(true, false), b.laps.map { it.manual })
        assertEquals(141, b.laps[1].hrAvg)
        assertEquals(listOf(0, 1, 2, 3), b.samples.map { it.idx })
        assertNull(b.samples[2].hr)
        assertEquals(121, b.samples[1].hr)
        assertTrue(b.laps.all { it.startMs == WorkoutTestData.START && it.sport == 1 })
    }

    @Test fun anInvalidStartTimeStillGetsAKeyFromTheEnd() {
        val s = WorkoutTestData.session(start = null)
        assertEquals(-4L, WorkoutMapper.keyMs(s))
        assertEquals(-4L, WorkoutMapper.map(s, 1L).workout.startMs)
    }
}
