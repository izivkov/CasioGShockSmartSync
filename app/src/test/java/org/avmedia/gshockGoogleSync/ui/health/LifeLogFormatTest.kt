package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockapi.model.StairsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LifeLogFormatTest {
    private fun t(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m)

    @Test fun minutesBecomeHoursAndMinutes() {
        assertEquals("7:34", formatMinutes(454)); assertEquals("0:05", formatMinutes(5)); assertEquals("0:00", formatMinutes(0))
    }
    @Test fun spo2SummaryHasTheNewestReadingAndTheRange() {
        val s = spo2Summary(listOf(Spo2Reading(t(24, 8, 0), 97), Spo2Reading(t(24, 6, 0), 94), Spo2Reading(t(24, 7, 0), 98)))!!
        assertEquals(97, s.latest.percent); assertEquals(94, s.min); assertEquals(98, s.max); assertEquals(3, s.count)
        assertNull(spo2Summary(emptyList()))
    }
    @Test fun stairsAreSummedOverTheDayOfTheNewestRecord() {
        val r = listOf(StairsRecord(t(23, 9, 0), 100, 100), StairsRecord(t(24, 8, 0), 3, 1), StairsRecord(t(24, 9, 0), 2, 4))
        assertEquals(5 to 5, stairsOfLatestDay(r)); assertNull(stairsOfLatestDay(emptyList()))
    }
    @Test fun todaysCardioLoadWinsOverTheStatusRecord() {
        val now = CardioStatus(LocalDate.of(2026, 11, 24), 1.5f, 2, 300, 400)
        val old = CardioStatus(LocalDate.of(2026, 11, 23), 0.5f, 1, 100, 200)
        assertEquals(now, cardioOf(BodyReadings(cardio = old, cardioNow = now)))
        assertEquals(old, cardioOf(BodyReadings(cardio = old)))
        assertNull(cardioOf(BodyReadings()))
    }
}
