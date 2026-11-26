package org.avmedia.gshockGoogleSync.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoredHealthTest {
    @Test
    fun storedHistoryGivesTodaysValuesWithoutAnyRead() {
        val days = listOf(DayRow("2026-11-21", steps = 100), DayRow("2026-11-22", steps = 4321, distanceM = 3100, kcal = 210, hrRest = 58))
        val hours = mapOf("2026-11-22" to listOf(HourRow("2026-11-22", 8, steps = 900), HourRow("2026-11-22", 9, steps = 1200)))
        val data = StoredHealth.stepData(days) { hours[it].orEmpty() }!!
        assertEquals(4321, data.currentDaySteps)
        assertEquals(3100, data.distanceMeters)
        assertEquals(210, data.h5600!!.energyKcal)
        assertEquals(58, data.h5600!!.heartRate!!.restingBpm)
        assertEquals(900, data.hourlySteps[8])
        assertEquals(1200, data.hourlySteps[9])
        assertEquals(22, data.dayOfMonth)
        assertNotNull(data.h5600)
    }

    @Test
    fun nothingStoredGivesNothing() {
        assertNull(StoredHealth.stepData(emptyList()) { emptyList() })
    }
}
