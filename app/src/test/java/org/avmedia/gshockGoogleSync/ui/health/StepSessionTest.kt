package org.avmedia.gshockGoogleSync.ui.health

import CachedIO
import java.io.File
import org.avmedia.gshockapi.model.StepCounterData
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The steps of the watches that count them (the ABL-100) are kept in [CachedIO] for the connection: the screen shows them at
 * once, and a read that fails or comes back empty never replaces them.
 */
class StepSessionTest {
    private fun steps(n: Int) = StepCounterData.unavailable().copy(currentDaySteps = n)

    @After fun reset() = CachedIO.clearCache()

    @Test fun aReadWithStepsIsTheConnectionsData() {
        assertNull(StepSession.data)
        val read = steps(4200)
        StepSession.store(read)
        assertSame(read, StepSession.data)
        assertSame("kept by the library's cache", read, CachedIO.getOrNull<StepCounterData>("STEP_COUNTER"))
    }

    @Test fun aReadWithoutStepsNeverReplacesTheLastGoodOne() {
        val good = steps(4200)
        StepSession.store(good)
        StepSession.store(StepCounterData.unavailable())
        assertSame(good, StepSession.data)
    }

    @Test fun anEmptyReadIsNotKept() {
        StepSession.store(StepCounterData.unavailable())
        assertNull(StepSession.data)
    }

    @Test fun aNewConnectionClearsIt() {
        StepSession.store(steps(100))
        CachedIO.clearCache() // what the library does when a connection is set up
        assertNull(StepSession.data)
    }

    @Test fun theScreenPollsWhileShownAndTheAblReadsThroughTheSession() {
        val screen = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/HealthScreen.kt").readText()
        assertTrue("onScreenShown()" in screen && "onScreenHidden()" in screen)
        val vm = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/HealthViewModel.kt").readText()
        assertTrue("if (!isDwH5600()) return readSteps(force)" in vm)
        assertTrue("requestStepRefresh(force = !isDwH5600())" in vm)
        val read = vm.substringAfter("private suspend fun readSteps").substringBefore("private fun onOpened")
        assertTrue("StepSession.store(read)" in read && "return StepSession.data ?: read" in read)
    }
}
