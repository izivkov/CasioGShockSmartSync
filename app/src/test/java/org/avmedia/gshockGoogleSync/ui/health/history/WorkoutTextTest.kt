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
    @Test fun unitsNotConfirmedSayUnconfirmed() {
        assertTrue(WorkoutText.distance(5.25).contains("unconfirmed"))
        assertTrue(WorkoutText.pace(343).contains("unconfirmed"))
        assertEquals("5:43 /km (unconfirmed)", WorkoutText.pace(343))
        assertTrue(WorkoutText.lines(WorkoutMapper.map(WorkoutTestData.session(), 1L).workout).first { it.first == "Distance" }.second.contains("unconfirmed"))
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

    @Test fun theReadNoteSaysWhatCame() {
        assertEquals("No new workout on the watch.", WorkoutText.downloadNote(0, null))
        assertEquals("2 new workouts stored.", WorkoutText.downloadNote(2, null))
        assertTrue(WorkoutText.downloadNote(1, "no answer").startsWith("Stopped: no answer"))
        assertTrue(WorkoutText.downloadNote(null, null).contains("already running"))
    }
}
