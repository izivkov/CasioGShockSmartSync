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

    @Test fun defaultPeriodIs15Minutes() = runTest {
        assertEquals(15 * 60_000L, HealthReadScheduler.DEFAULT_INTERVAL_MS)
        connect(); now += 14 * 60_000L
        assertEquals(Outcome.NOT_DUE, connect()); assertEquals(1, reads)
        now += 2 * 60_000L
        assertEquals(Outcome.OK, connect()); assertEquals(2, reads)
    }

    @Test fun periodFollowsTheSettingAndOffReadsNothing() = runTest {
        var minutes = 30L
        val sched = HealthReadScheduler(store, { now }, intervalMs = { minutes * 60_000L })
        sched.onConnection(true, true) { reads++; true }
        now += 20 * 60_000L
        assertEquals(Outcome.NOT_DUE, sched.onConnection(true, true) { reads++; true })
        minutes = 0L; now += 60 * 60_000L
        assertEquals(Outcome.OFF, sched.onConnection(true, true) { reads++; true }); assertEquals(1, reads)
    }

    @Test fun fiveMinutesIsAChoiceAndReadsEveryFiveMinutes() = runTest {
        assertEquals(listOf(5, 15, 30, 60, 0), HealthReadScheduler.CHOICES_MIN)
        val sched = HealthReadScheduler(store, { now }, intervalMs = { 5 * 60_000L })
        sched.onConnection(true, true) { reads++; true }
        now += 4 * 60_000L
        assertEquals(Outcome.NOT_DUE, sched.onConnection(true, true) { reads++; true })
        now += 60_000L
        assertEquals(Outcome.OK, sched.onConnection(true, true) { reads++; true }); assertEquals(2, reads)
    }

    @Test fun skippedWhileAWatchActionOrExchangeRuns() = runTest {
        var busy = true
        val sched = HealthReadScheduler(store, { now }, busy = { busy })
        assertEquals(Outcome.BUSY, sched.onConnection(true, true) { reads++; true }); assertEquals(0, reads)
        busy = false
        assertEquals(Outcome.OK, sched.onConnection(true, true) { reads++; true }); assertEquals(1, reads)
    }

    @Test fun aRunningReadIsNeverJoinedByASecond() = runTest(UnconfinedTestDispatcher()) {
        val shared = AtomicBoolean(false)
        val a = HealthReadScheduler(store, { now }, shared)
        val b = HealthReadScheduler(Mem(), { now }, shared)
        val gate = CompletableDeferred<Unit>()
        val first = launch { a.onConnection(true, true) { reads++; gate.await(); true } }
        assertEquals(Outcome.BUSY, b.onConnection(true, true) { reads++; true }); assertEquals(1, reads)
        gate.complete(Unit); first.join()
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
}
