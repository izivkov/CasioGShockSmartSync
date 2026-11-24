package org.avmedia.smartsync.plugin.api

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A range is half open: adjoining days neither overlap nor leave the last second out (review S11). */
class HistoryRangeTest {
    private val d = LocalDate.of(2026, 11, 24)

    @Test fun aDayRangeHoldsTheWholeDayAndNotTheNextMidnight() {
        val r = HistoryRange.days(d, d)
        assertTrue(r.contains(d.atStartOfDay()))
        assertTrue(r.contains(d.atTime(23, 59, 59, 999_000_000)))
        assertFalse(r.contains(d.plusDays(1).atStartOfDay()))
        assertEquals(d.plusDays(1).atStartOfDay(), r.to)
    }

    @Test fun adjoiningRangesShareNoInstant() {
        val a = HistoryRange.days(d, d)
        val b = HistoryRange.days(d.plusDays(1), d.plusDays(1))
        val edge = d.plusDays(1).atStartOfDay()
        assertTrue(a.contains(edge) != b.contains(edge))
        assertFalse("an empty range holds nothing", HistoryRange(edge, edge).contains(edge))
    }
}
