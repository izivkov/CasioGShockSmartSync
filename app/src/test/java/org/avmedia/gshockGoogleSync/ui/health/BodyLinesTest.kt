package org.avmedia.gshockGoogleSync.ui.health

import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.BreathingSession
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockGoogleSync.ui.health.history.ChartLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyLinesTest {
    @Test fun aLineOnlyWhereTheWatchSentAValue() {
        assertTrue(BodyLines.lines(null).isEmpty())
        assertTrue(BodyLines.lines(BodyReadings()).isEmpty())
        val lines = BodyLines.lines(
            BodyReadings(
                spo2 = listOf(Spo2Reading(LocalDateTime.of(2026, 11, 23, 7, 10), 97)),
                cardioNow = CardioStatus(LocalDate.of(2026, 11, 23), 1.25f, 2, null, 410),
                breathingSessions = listOf(BreathingSession(LocalDateTime.of(2026, 11, 22, 21, 0), emptyList())),
            ),
        ).toMap()
        assertTrue(lines.getValue("Blood oxygen").startsWith("97 %"))
        assertEquals("1.25 today", lines["Cardio load ratio"])
        assertEquals("2 of 4", lines["Cardio status"])
        assertEquals("410", lines["Tolerance"])
        assertTrue("Strain" !in lines)
        assertTrue("Breathing exercise" in lines)
    }

    @Test fun anOlderCardioRecordSaysItsDate() {
        val lines = BodyLines.lines(BodyReadings(cardio = CardioStatus(LocalDate.of(2026, 11, 21), 0.5f, null, null, null))).toMap()
        assertEquals("0.50 (2026-11-21)", lines["Cardio load ratio"])
    }

    @Test fun theDayChartIsLabelledEverySixHours() {
        val labels = ChartLabels.daySlots(288)
        assertEquals(288, labels.size)
        assertEquals(listOf("0h", "6h", "12h", "18h"), labels.filterNotNull())
        assertEquals("6h", labels[72])
    }
}
