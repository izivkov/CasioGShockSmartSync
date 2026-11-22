package org.avmedia.gshockGoogleSync.ui.health

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.avmedia.gshockGoogleSync.ui.health.HealthReadScheduler.Outcome

class HealthReadSchedulerTest {
    private class Mem : HealthReadStore {
        override var lastSuccessMs = 0L
        override var lastFailureMs = 0L
    }

    private var now = 10_000_000L
    private val store = Mem()
    private val s = HealthReadScheduler(store, { now })
    private var reads = 0

    private suspend fun connect(h5600: Boolean = true, consumer: Boolean = true, ok: Boolean = true) =
        s.onConnection(h5600, consumer) { reads++; ok }

    @Test fun readsWhenDueOnConnection() = runTest {
        assertEquals(Outcome.OK, connect()); assertEquals(1, reads)
    }

    @Test fun notAgainWithin2Hours() = runTest {
        connect(); now += 119 * 60_000L
        assertEquals(Outcome.NOT_DUE, connect()); assertEquals(1, reads)
        now += 2 * 60_000L
        assertEquals(Outcome.OK, connect()); assertEquals(2, reads)
    }

    @Test fun notTwiceAtOnce() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        val first = launch { s.onConnection(true, true) { reads++; gate.await(); true } }
        assertEquals(Outcome.BUSY, connect()); assertEquals(1, reads)
        gate.complete(Unit); first.join()
    }

    @Test fun failedReadRetriesAfter5Minutes() = runTest {
        assertEquals(Outcome.FAILED, connect(ok = false))
        now += 4 * 60_000L
        assertEquals(Outcome.NOT_DUE, connect()); assertEquals(1, reads)
        now += 2 * 60_000L
        assertEquals(Outcome.OK, connect()); assertEquals(2, reads)
    }

    @Test fun thrownReadCountsAsFailure() = runTest {
        assertEquals(Outcome.FAILED, s.onConnection(true, true) { error("boom") })
    }

    @Test fun skippedForOtherModels() = runTest {
        assertEquals(Outcome.SKIPPED_MODEL, connect(h5600 = false)); assertEquals(0, reads)
    }

    @Test fun noConsumerNoRead() = runTest {
        assertEquals(Outcome.NO_CONSUMER, connect(consumer = false)); assertEquals(0, reads)
    }

    @Test fun consumerRules() {
        val c = HealthReadScheduler.Companion::hasConsumer
        assertTrue(c(true, true, true, false, false))
        assertFalse(c(false, true, true, false, false))
        assertFalse(c(true, false, true, false, false))
        assertFalse(c(true, true, false, false, false))
        assertTrue(c(false, false, false, true, true))
        assertFalse(c(false, false, false, false, true))
        assertFalse(c(false, false, false, true, false))
    }
}
