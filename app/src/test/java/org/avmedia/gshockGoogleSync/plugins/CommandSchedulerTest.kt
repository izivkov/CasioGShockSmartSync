package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockGoogleSync.plugins.CommandScheduler.Companion.DONE
import org.avmedia.gshockGoogleSync.plugins.CommandScheduler.Companion.NOT_CONNECTED
import org.avmedia.gshockGoogleSync.plugins.CommandScheduler.Companion.QUEUED
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.WatchId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class CommandSchedulerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val w = WatchId("gshock-aabbccddeeff")
    private val other = WatchId("gshock-112233445566")
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @After fun tearDown() = scope.cancel()

    private fun scheduler(
        connected: (WatchId) -> Boolean = { true },
        transfer: () -> Boolean = { false },
        clock: () -> Long = System::currentTimeMillis,
        timeoutMs: Long = 2_000,
        busyMs: Long = 2_000,
    ) = CommandScheduler(scope, connected, transfer, { p, l -> lines += "$p: $l" }, clock, timeoutMs, busyMs, pollMs = 5)

    @Test fun commandsFromTwoPluginsNeverRunAtTheSameTimeAndTakeTurns() = runBlocking {
        val s = scheduler()
        val running = AtomicInteger(0)
        var overlap = false
        val order: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val gate = CompletableDeferred<Unit>()
        fun work(name: String): suspend () -> WriteResult = {
            if (running.incrementAndGet() > 1) overlap = true
            if (name == "a1") gate.await()
            delay(5)
            order += name
            running.decrementAndGet()
            DONE
        }
        val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { s.submit("a", w, Delivery.Now, "a1", work("a1")) }
        delay(50) // a1 runs and holds the queue
        val rest = listOf("a2", "a3", "b1", "b2", "b3").map { n -> async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { s.submit(n.take(1), w, Delivery.Now, n, work(n)) } }
        delay(50)
        gate.complete(Unit)
        (rest + first).awaitAll().forEach { assertEquals(DONE, it) }
        assertTrue("two writes ran at once", !overlap)
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3", "b3"), order.toList())
    }

    @Test fun nothingRunsDuringATransferAndALongTransferGivesBusy() = runBlocking {
        var transfer = true
        val s = scheduler(transfer = { transfer }, busyMs = 100)
        assertEquals(FailReason.BUSY, (s.submit("a", w, Delivery.Now, "x") { DONE } as PluginResult.Failed).reason)
        val ran = async { s.submit("a", w, Delivery.Now, "y") { DONE } }
        delay(30)
        transfer = false
        assertEquals(DONE, ran.await())
    }

    @Test fun aWatchThatIsNotConnectedGivesNotConnected() = runBlocking {
        val s = scheduler(connected = { false })
        var ran = false
        assertEquals(NOT_CONNECTED, s.submit("a", w, Delivery.Now, "x") { ran = true; DONE })
        assertTrue(!ran)
    }

    @Test fun aWriteThatHangsEndsWithAFailureAndTheNextOneStillRuns() = runBlocking {
        val s = scheduler(timeoutMs = 50)
        val r = s.submit("a", w, Delivery.Now, "x") { awaitCancellation() }
        assertTrue(r is PluginResult.Failed)
        assertEquals(DONE, s.submit("b", w, Delivery.Now, "y") { DONE })
    }

    @Test fun aThrowingWriteIsAFailureNotACrash() = runBlocking {
        val s = scheduler()
        assertTrue(s.submit("a", w, Delivery.Now, "x") { error("boom") } is PluginResult.Failed)
        assertEquals(DONE, s.submit("a", w, Delivery.Now, "y") { DONE })
    }

    @Test fun nextConnectionWritesWaitForTheirWatchAndRunWhenItConnects() = runBlocking {
        val up = mutableSetOf<WatchId>()
        val s = scheduler(connected = { it in up })
        val ran: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val later = Delivery.NextConnection(key = "face")
        assertEquals(QUEUED, s.submit("a", w, later, "face 1") { ran += "face 1"; DONE })
        assertEquals(QUEUED, s.submit("a", w, later, "face 2") { ran += "face 2"; DONE })
        assertEquals(QUEUED, s.submit("a", w, Delivery.NextConnection(), "note") { ran += "note"; DONE })
        assertEquals(QUEUED, s.submit("a", other, Delivery.NextConnection(), "other") { ran += "other"; DONE })
        up += w
        s.onConnected(w)
        withTimeout(2_000) { while (ran.size < 2) delay(5) }
        delay(50)
        assertEquals("the newer write of the same key replaced the older one; the other watch's waits", listOf("face 2", "note"), ran.toList())
        assertEquals(1, s.parkedCount())
    }

    @Test fun anExpiredNextConnectionWriteIsDroppedWithALine() = runBlocking {
        var now = 0L
        val up = mutableSetOf<WatchId>()
        val s = scheduler(connected = { it in up }, clock = { now })
        var ran = false
        s.submit("a", w, Delivery.NextConnection(keepMillis = 1_000), "note") { ran = true; DONE }
        now = 5_000
        up += w
        s.onConnected(w)
        delay(50)
        assertTrue(!ran)
        assertTrue(lines.any { it.contains("expired") })
    }

    @Test fun aStoppedPluginsWritesAreDropped() = runBlocking {
        val s = scheduler(connected = { false })
        s.submit("a", w, Delivery.NextConnection(), "note") { DONE }
        s.drop("a")
        assertEquals(0, s.parkedCount("a"))
    }
}
