package org.avmedia.gshockGoogleSync.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimelineTest {
    private val lines = "2026-11-20T00:10:00,light\n2026-11-20T01:00:00,deep\n2026-11-20T03:00:00,rem"

    @Test fun eachStageLastsUntilTheNextAndTheLastUntilWakeUp() {
        val s = SleepTimeline.segments(lines, "2026-11-20T07:10:00")
        assertEquals(
            listOf(StageSegment("light", 0, 50), StageSegment("deep", 50, 170), StageSegment("rem", 170, 420)),
            s,
        )
    }

    @Test fun theLastStageIsLeftOutWithoutAnEnd() {
        assertEquals(2, SleepTimeline.segments(lines, null).size)
    }

    @Test fun noTimelineOrBadLinesGiveNothing() {
        assertTrue(SleepTimeline.segments(null, null).isEmpty())
        assertTrue(SleepTimeline.segments("junk\n,\nx,y", "2026-11-20T07:10:00").isEmpty())
    }
}
