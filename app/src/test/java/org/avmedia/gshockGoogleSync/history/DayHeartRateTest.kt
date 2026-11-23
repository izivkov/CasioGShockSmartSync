package org.avmedia.gshockGoogleSync.history

import androidx.room.Room
import java.time.LocalDate
import java.time.LocalDateTime
import org.avmedia.gshockapi.model.H5600HealthData
import org.avmedia.gshockapi.model.HeartRateSlot
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The day's heart rate curve: partial reads merge into one day, nothing after now, max and min with their times. */
@RunWith(RobolectricTestRunner::class)
class DayHeartRateTest {
    private val day = LocalDate.of(2026, 11, 23)
    private fun at(h: Int, m: Int) = day.atTime(h, m)
    private fun slot(h: Int, m: Int, bpm: Int) = HeartRateSlot(at(h, m), bpm)

    private fun read(time: LocalDateTime, vararg slots: HeartRateSlot) = StepCounterData(
        timestamp = time, dayOfWeek = 1, month = 11, dayOfMonth = 23, hourlySteps = emptyList(), dailyHistory = emptyList(), currentDaySteps = null,
        h5600 = H5600HealthData(heartRateSlots = slots.toList()),
    )

    @Test fun twoPartialTransfersMergeIntoOneDayThroughTheDatabase() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDb::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.dao()
            // the first read carries 08:00 to 08:04, the second only what came since (08:06, 08:08)
            val first = read(at(8, 5), slot(8, 0, 60), slot(8, 2, 62), slot(8, 4, 64))
            val second = read(at(8, 9), slot(8, 6, 90), slot(8, 8, 66))
            dao.upsert(HistoryMapper.map(first))
            val merged = DayHeartRate.merge(second, dao.hrSlotsOf(day.toString()), now = at(8, 9))
            assertEquals(listOf(60, 62, 64, 90, 66), merged.h5600!!.heartRateSlots.map { it.bpm })
            dao.upsert(HistoryMapper.map(second))
            assertEquals(5, dao.hrSlotsOf(day.toString()).size)
            // the same slot read again is one row with the newest value
            dao.upsert(HistoryMapper.map(read(at(8, 9), slot(8, 8, 70))))
            assertEquals(listOf(60, 62, 64, 90, 70), dao.hrSlotsOf(day.toString()).map { it.bpm })
        } finally {
            db.close()
        }
    }

    @Test fun nothingAfterNowAndNothingOfAnotherDay() {
        val stored = listOf(HrSlotRow(day.toString(), 20 * 60, 80), HrSlotRow(day.minusDays(1).toString(), 9 * 60, 99))
        val merged = DayHeartRate.merge(listOf(slot(9, 0, 70), HeartRateSlot(day.minusDays(1).atTime(23, 58), 55)), stored, day, now = at(12, 0))
        assertEquals(listOf(at(9, 0)), merged.map { it.time })
    }

    @Test fun maxAndMinComeWithTheirTimesTheEarliestOnATie() {
        val slots = listOf(slot(3, 10, 48), slot(9, 0, 120), slot(14, 32, 154), slot(16, 0, 154), slot(22, 0, 48))
        assertEquals(at(14, 32), DayHeartRate.highest(slots)!!.time)
        assertEquals(154, DayHeartRate.highest(slots)!!.bpm)
        assertEquals(at(3, 10), DayHeartRate.lowest(slots)!!.time)
        assertNull(DayHeartRate.highest(emptyList()))
    }

    @Test fun homeAssistantGets288FiveMinuteValuesFromMidnight() {
        val v = DayHeartRate.perFiveMinutes(listOf(slot(0, 0, 60), slot(0, 2, 63), slot(0, 4, 70), slot(0, 6, 90), slot(23, 58, 80)))
        assertEquals(288, v.size)
        assertEquals(64, v[0]) // 00:00, 00:02 and 00:04 start in the first 5 minutes: mean 64.3
        assertEquals(90, v[1])
        assertEquals(80, v[287])
        assertTrue(v.subList(2, 287).all { it == null })
    }
}
