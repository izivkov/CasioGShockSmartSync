package org.avmedia.gshockGoogleSync.plugins

import io.mockk.mockk
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.avmedia.smartsync.plugin.api.ConnectionKind
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The runtime guards of the plugin interface review (2026-11-24): revoked hosts, blocked and cancelled callbacks, limits. */
class SpiRuntimeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val w = WatchId("gshock-aabbccddeeff")

    @After fun tearDown() = scope.cancel()

    private open class Fake(
        open val pid: String,
        override val access: PluginAccess = PluginAccess(reads = DataUse.entries.toSet(), controls = Control.entries.toSet()),
        val start: suspend () -> Unit = {},
        val handle: suspend (WatchEvent) -> Unit = {},
    ) : WatchPlugin {
        override val id: String get() = pid
        override val name: String get() = pid
        override val version = "1"
        override val apiVersion = PluginApi.VERSION
        val seen: MutableList<WatchEvent> = Collections.synchronizedList(mutableListOf())
        @Volatile var running = false
        @Volatile var deactivatedWhileRunning: Boolean? = null
        override suspend fun activate(host: PluginHost) = start()
        override suspend fun deactivate() { deactivatedWhileRunning = running }
        override suspend fun onEvent(event: WatchEvent) { handle(event); seen += event }
    }

    private class Revocable : PluginHost by mockk<PluginHost>(relaxed = true), RevocableHost {
        @Volatile var revoked = false
        override fun revoke() { revoked = true }
    }

    private fun runtime(limits: PluginLimits = PluginLimits(graceMs = 300), hosts: MutableList<Revocable> = mutableListOf(), dropped: MutableList<String> = mutableListOf()) =
        PluginRuntime(scope, hostFor = { Revocable().also { hosts += it } }, log = { n, l -> lines += "$n: $l" }, limits = limits,
            dropCommands = { dropped += it })

    private suspend fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        runCatching { withTimeout(ms) { while (!ok()) delay(10) } }.onFailure { throw AssertionError("timed out: $what") }
    }

    // B3: the host a plugin holds is revoked when it stops.
    @Test fun stoppingRevokesThePluginsHost() = runBlocking {
        val hosts = mutableListOf<Revocable>()
        val rt = runtime(hosts = hosts)
        rt.register(listOf(Fake("p")))
        rt.activate("p")
        assertFalse(hosts.single().revoked)
        rt.deactivate("p")
        assertTrue("the kept host is revoked", hosts.single().revoked)
        rt.activate("p")
        assertEquals("a new host per activation", 2, hosts.size)
        assertFalse(hosts[1].revoked)
    }

    @Test fun theSchedulerAnswersStoppedForADroppedWrite() = runBlocking {
        val s = CommandScheduler(scope, connected = { false }, transferRunning = { false })
        val r = async { s.submit("p", w, Delivery.Now, "x") { CommandScheduler.DONE } }
        assertEquals(CommandScheduler.NOT_CONNECTED, r.await())
        val parked = async { s.submit("p", w, Delivery.NextConnection(), "x") { CommandScheduler.DONE } }
        assertEquals(CommandScheduler.QUEUED, parked.await())
        assertEquals(1, s.parkedCount("p"))
        s.drop("p")
        assertEquals(0, s.parkedCount("p"))
    }

    // H1: a plugin blocked in activate (not suspending) holds only its own start.
    @Test fun aBlockedStartDoesNotHoldTheOtherPlugins() = runBlocking {
        val inside = CompletableDeferred<Unit>()
        val sleepy = Fake("sleepy", start = { inside.complete(Unit); Thread.sleep(3_000) })
        val quick = Fake("quick")
        val rt = runtime(PluginLimits(activateTimeoutMs = 200, graceMs = 1_500))
        rt.register(listOf(sleepy, quick))
        val first = scope.async { rt.activate("sleepy") }
        withTimeout(2_000) { inside.await() }
        val t0 = System.currentTimeMillis()
        assertTrue(rt.activate("quick"))
        val took = System.currentTimeMillis() - t0
        assertTrue("the second plugin started in $took ms", took < 1_000)
        assertFalse(first.await())
        assertEquals(PluginState.Failed("blocked while starting"), rt.state("sleepy"))
    }

    @Test fun aCallbackBlockedInAnEventTurnsThePluginOffAtOnce() = runBlocking {
        val p = Fake("stuck", handle = { Thread.sleep(2_000) })
        val rt = runtime(PluginLimits(eventTimeoutMs = 100, graceMs = 200, stopWaitMs = 100))
        rt.register(listOf(p))
        rt.activate("stuck")
        rt.dispatch(WatchEvent.Disconnected(w))
        until("turned off") { rt.state("stuck") is PluginState.Failed }
        assertTrue(lines.toString(), lines.any { it.contains("blocked") })
    }

    // H2: the plugin's own CancellationException is a failure, not the end of its queue.
    @Test fun aPluginsOwnCancellationIsCountedAndTheQueueGoesOn() = runBlocking {
        var first = true
        val p = Fake("c", handle = { if (first) { first = false; throw kotlinx.coroutines.CancellationException("its own job") } })
        val rt = runtime()
        rt.register(listOf(p))
        rt.activate("c")
        rt.dispatch(WatchEvent.Disconnected(w))
        rt.dispatch(WatchEvent.Connected(w, ConnectionKind.USER))
        until("the second event arrived") { p.seen.size == 1 }
        assertEquals(PluginState.Active, rt.state("c"))
        assertTrue(lines.any { it.contains("Disconnected failed") })
    }

    // H3: deactivate never runs while onEvent does.
    @Test fun deactivateWaitsForARunningEvent() = runBlocking {
        lateinit var p: Fake
        p = Fake("slowstop", handle = {
            p.running = true
            try { awaitCancellation() } finally { withContext(NonCancellable) { delay(300) }; p.running = false }
        })
        val rt = runtime()
        rt.register(listOf(p))
        rt.activate("slowstop")
        rt.dispatch(WatchEvent.Disconnected(w))
        until("onEvent runs") { p.running }
        rt.deactivate("slowstop")
        assertEquals(false, p.deactivatedWhileRunning)
    }

    // H4: time in host calls does not count against the callback's limit.
    @Test fun timeInAHostCallDoesNotCount() = runBlocking {
        lateinit var rt: PluginRuntime
        val p = Fake("waits", handle = { rt.enterHost("waits"); try { delay(400) } finally { rt.exitHost("waits") } })
        rt = runtime(PluginLimits(eventTimeoutMs = 150, graceMs = 300))
        rt.register(listOf(p))
        rt.activate("waits")
        rt.dispatch(WatchEvent.Disconnected(w))
        until("handled") { p.seen.size == 1 }
        assertTrue(lines.toString(), lines.none { it.contains("failed") })
    }

    @Test fun aCancelledCallersWriteIsNeverWrittenLater() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var lateRan = false
        val s = CommandScheduler(scope, connected = { true }, transferRunning = { false })
        val holder = async { s.submit("a", w, Delivery.Now, "hold") { gate.await(); CommandScheduler.DONE } }
        delay(50)
        val caller = scope.launch { s.submit("b", w, Delivery.Now, "late") { lateRan = true; CommandScheduler.DONE } }
        delay(50)
        assertEquals(1, s.queuedCount("b"))
        caller.cancel()
        delay(50)
        assertEquals(0, s.queuedCount("b"))
        gate.complete(Unit)
        holder.await()
        delay(100)
        assertFalse(lateRan)
    }

    @Test fun aPluginsQueueIsBounded() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val s = CommandScheduler(scope, connected = { true }, transferRunning = { false }, maxQueuedPerPlugin = 3)
        val holder = scope.async { s.submit("a", w, Delivery.Now, "hold") { gate.await(); CommandScheduler.DONE } }
        delay(50)
        val waiting = (1..3).map { scope.async { s.submit("a", w, Delivery.Now, "w$it") { CommandScheduler.DONE } } }
        delay(50)
        val over = s.submit("a", w, Delivery.Now, "over") { CommandScheduler.DONE }
        assertEquals(FailReason.BUSY, (over as PluginResult.Failed).reason)
        gate.complete(Unit)
        holder.await(); waiting.forEach { it.await() }
    }

    @Test fun aHugeKeepTimeDoesNotExpireAtOnce() = runBlocking {
        var up = false
        var ran = false
        val s = CommandScheduler(scope, connected = { up }, transferRunning = { false })
        assertEquals(CommandScheduler.QUEUED, s.submit("a", w, Delivery.NextConnection(keepMillis = Long.MAX_VALUE), "x") { ran = true; CommandScheduler.DONE })
        up = true
        s.onConnected(w)
        until("written at the connection") { ran }
    }

    // H7: events during activate are queued, a Connected seen then is not sent twice.
    @Test fun eventsDuringTheStartAreQueued() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val p = Fake("late", start = { gate.await() })
        val rt = runtime()
        rt.register(listOf(p))
        val starting = scope.async { rt.activate("late") }
        delay(50)
        rt.dispatch(WatchEvent.Connected(w, ConnectionKind.WATCH))
        rt.dispatch(WatchEvent.Disconnected(w))
        rt.dispatch(WatchEvent.Connected(w, ConnectionKind.WATCH))
        gate.complete(Unit)
        assertTrue(starting.await())
        rt.dispatchTo("late", WatchEvent.Connected(w, ConnectionKind.USER))
        until("three events") { p.seen.size == 3 }
        delay(100)
        assertEquals(listOf("Connected", "Disconnected", "Connected"), p.seen.map { it.javaClass.simpleName })
        assertEquals(ConnectionKind.WATCH, (p.seen.last() as WatchEvent.Connected).kind)
    }

    // H9: a need the declaration does not cover counts for nothing.
    @Test fun healthReadsNeedAHealthKind() = runBlocking {
        val rt = runtime()
        rt.register(listOf(Fake("phone", access = PluginAccess(reads = setOf(DataUse.PHONE)))))
        rt.activate("phone")
        rt.declareNeeds("phone", PluginNeeds(healthReads = true))
        assertFalse(rt.needs().healthReads)
    }

    // H10: the id is the one read at registration.
    @Test fun aPluginWhoseIdChangesKeepsItsFirstId() = runBlocking {
        val dropped = mutableListOf<String>()
        val p = object : Fake("first") { var now = "first"; override val pid: String get() = now }
        val rt = runtime(dropped = dropped)
        rt.register(listOf(p))
        p.now = "other"
        assertTrue(rt.activate("first"))
        rt.deactivate("first")
        assertEquals(listOf("first"), dropped)
    }

    @Test fun reservedIdsAreSkipped() {
        val rt = runtime()
        rt.register(listOf(Fake("app"), Fake("webhooks"), Fake("ok")))
        assertEquals(listOf("ok"), rt.ids())
    }

    // S4: a wake-up when its interval passed, never more often.
    @Test fun aPluginIsWokenOnItsInterval() = runBlocking {
        val p = Fake("timer")
        val q = Fake("quiet")
        val rt = runtime()
        rt.register(listOf(p, q))
        rt.activate("timer"); rt.activate("quiet")
        rt.declareNeeds("timer", PluginNeeds(wakeEveryMinutes = 5))
        assertEquals("at least 15 minutes", 15, rt.needs().wakeEveryMinutes)
        val t = 1_000_000_000L
        assertEquals(listOf("timer"), rt.wake(t))
        assertEquals(emptyList<String>(), rt.wake(t + 10 * 60_000))
        assertEquals(listOf("timer"), rt.wake(t + 15 * 60_000))
        until("two wake-ups") { p.seen.count { it == WatchEvent.Wake } == 2 }
        assertTrue(q.seen.none { it == WatchEvent.Wake })
    }
}
