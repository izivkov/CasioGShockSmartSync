package org.avmedia.gshockGoogleSync.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** A workout delivered again (a later Health read, a mark that did not reach the watch) is kept once and pushed to Home Assistant once. */
@RunWith(RobolectricTestRunner::class)
class WorkoutStoreTest {
    private val saved = WorkoutStore.onDownloaded

    @After fun restore() {
        WorkoutStore.onDownloaded = saved
    }

    @Test fun theSameWorkoutTwiceIsOneRowAndOneHomeAssistantUpdate() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        var pushes = 0
        WorkoutStore.onDownloaded = { pushes++ }
        val start = WorkoutTestData.START + System.nanoTime() % 1_000_000_000L * 1000
        val session = WorkoutTestData.session(start = start)

        val (first, second) = withContext(Dispatchers.IO) { WorkoutStore.save(context, session) to WorkoutStore.save(context, session) }

        assertTrue("stored, so the watch may mark it", first && second)
        val rows = withContext(Dispatchers.IO) { HistoryDb.get(context).dao().allWorkouts().filter { it.startMs == start } }
        assertEquals(1, rows.size)
        assertEquals("one Home Assistant update", 1, pushes)
    }
}
