package org.avmedia.gshockGoogleSync.history

import androidx.room.Room
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HistoryDbTest {
    private lateinit var db: HistoryDb
    private val dao get() = db.dao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun readingTheSameDataAgainAddsNoRows() {
        val batch = HistoryMapper.map(HistoryTestData.transfer())
        repeat(3) { dao.upsert(batch) }
        assertEquals(1, dao.allDays().size)
        assertEquals(5, dao.allHours().size)
        assertEquals(1, dao.allNights().size)
        assertEquals(5000, dao.allDays().single().steps)
    }

    @Test fun aLaterReadUpdatesTheSameDayAndNight() {
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer(steps = 5000)))
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer(steps = 7000, hours = mapOf(12 to 2000), night = HistoryTestData.night(score = 88))))
        assertEquals(1, dao.allDays().size)
        assertEquals(7000, dao.allDays().single().steps)
        assertEquals(88, dao.allNights().single().score)
        assertEquals(2000, dao.hoursOf("2026-11-20").single { it.hour == 12 }.steps)
        assertEquals(800, dao.hoursOf("2026-11-20").single { it.hour == 8 }.steps)
    }

    @Test fun aPartialTransferNeverLowersTheDay() {
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer(steps = 7000)))
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer(steps = 3000, hours = mapOf(8 to 100))))
        assertEquals(7000, dao.allDays().single().steps)
        assertEquals(800, dao.hoursOf("2026-11-20").single { it.hour == 8 }.steps)
    }

    @Test fun daysWithoutAReadStayEmptyNotZero() {
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer()))
        val end = LocalDate.of(2026, 11, 22)
        val steps = HistorySeries.days(dao.allDays(), end, 7) { it.steps }
        assertEquals(7, steps.size)
        assertEquals(5000f, steps[4])
        assertNull(steps[5])
        assertNull(steps[6])
        assertNull(steps[0])
        assertEquals(1, dao.allDays().size)
    }

    @Test fun nextDayGetsItsOwnRow() {
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer()))
        dao.upsert(HistoryMapper.map(HistoryTestData.transfer(steps = 900, at = HistoryTestData.noon.plusDays(1), night = null)))
        assertEquals(listOf("2026-11-20", "2026-11-21"), dao.allDays().map { it.date })
    }
}
