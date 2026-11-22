package org.avmedia.gshockapi.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectWaitTest {
    @Test fun aWriteWaitsForTheLinkAndGoesAheadWhenItIsBack() = runTest {
        var up = false
        val result = async { ReconnectWait.awaitLink({ up }) }
        testScheduler.advanceTimeBy(60_000)
        assertFalse(result.isCompleted)
        up = true
        testScheduler.advanceTimeBy(ReconnectWait.POLL_MS + 1)
        assertTrue(result.await())
    }

    @Test fun aWriteFailsAfterTheWindowWhenTheLinkNeverComes() = runTest {
        assertFalse(ReconnectWait.awaitLink({ false }))
        assertEquals(ReconnectWait.WINDOW_MS, testScheduler.currentTime)
    }
}
