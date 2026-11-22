package org.avmedia.gshockGoogleSync.history

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkoutDbTest {
    private lateinit var db: HistoryDb
    private val dao get() = db.dao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun theSameWorkoutDeliveredThreeTimesIsOneRowWithItsLapsOnce() {
        val batch = WorkoutMapper.map(WorkoutTestData.session(), 1L)
        repeat(3) { dao.upsert(batch) }
        assertEquals(1, dao.allWorkouts().size)
        assertEquals(2, dao.allLaps().size)
        assertEquals(4, dao.allSamples().size)
    }

    @Test fun aLaterReadReplacesTheSetsInsteadOfAddingToThem() {
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(laps = 3, samples = 6), 1L))
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(laps = 2, samples = 4, kcal = 500), 2L))
        assertEquals(1, dao.allWorkouts().size)
        assertEquals(500L, dao.allWorkouts().single().kcal)
        assertEquals(2, dao.lapsOf(WorkoutTestData.START, 1).size)
        assertEquals(4, dao.samplesOf(WorkoutTestData.START, 1).size)
    }

    @Test fun anotherSportAtTheSameStartAndAnotherStartAreTheirOwnRows() {
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(sport = 1), 1L))
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(sport = 8), 1L))
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(start = WorkoutTestData.START + 86_400_000), 1L))
        assertEquals(3, dao.allWorkouts().size)
        assertEquals(6, dao.allLaps().size)
    }

    @Test fun aGapStaysNullNotZero() {
        dao.upsert(WorkoutMapper.map(WorkoutTestData.session(kcal = null, hrAvg = null, distance = null), 1L))
        val w = dao.allWorkouts().single()
        assertNull(w.kcal)
        assertNull(w.hrAvg)
        assertNull(w.distance)
        assertNull(dao.samplesOf(WorkoutTestData.START, 1)[2].hr)
    }
}
