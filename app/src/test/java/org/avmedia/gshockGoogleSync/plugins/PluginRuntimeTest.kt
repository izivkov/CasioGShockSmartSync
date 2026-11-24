package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.mockk.mockk
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.avmedia.smartsync.plugin.api.WatchValues
import org.avmedia.smartsync.plugin.api.ValueCause
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class PluginRuntimeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val w = WatchId("gshock-aabbccddeeff")

    @After fun tearDown() = scope.cancel()

    private open class Fake(
        override val id: String,
        override val apiVersion: String = PluginApi.VERSION,
        val handle: suspend (WatchEvent) -> Unit = {},
    ) : WatchPlugin {
        override val name: String get() = id
        override val version: String = "1"
        override val access = org.avmedia.smartsync.plugin.api.PluginAccess(
            reads = org.avmedia.smartsync.plugin.api.DataUse.entries.toSet(), controls = org.avmedia.smartsync.plugin.api.Control.entries.toSet(),
        )
        val seen: MutableList<WatchEvent> = Collections.synchronizedList(mutableListOf())
        var host: PluginHost? = null
        var deactivated = 0
        override suspend fun activate(host: PluginHost) { this.host = host }
        override suspend fun deactivate() { deactivated++ }
        override suspend fun onEvent(event: WatchEvent) { handle(event); seen += event }
    }

    private fun runtime(limits: PluginLimits = PluginLimits(), failed: MutableList<String> = mutableListOf(), dropped: MutableList<String> = mutableListOf()) =
        PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { n, l -> lines += "$n: $l" }, limits = limits,
            onFailed = { id, _ -> failed += id }, dropCommands = { dropped += it })

    private suspend fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        runCatching { withTimeout(ms) { while (!ok()) delay(10) } }.onFailure { throw AssertionError("timed out: $what") }
    }

    @Test fun aBlockedPluginDoesNotDelayAnotherOnesEvents() = runBlocking {
        val blocked = Fake("blocked") { awaitCancellation() }
        val quick = Fake("quick")
        val rt = runtime()
        rt.register(listOf(blocked, quick))
        assertTrue(rt.activate("blocked"))
        assertTrue(rt.activate("quick"))
        repeat(3) { rt.dispatch(WatchEvent.Connected(w, org.avmedia.smartsync.plugin.api.ConnectionKind.USER)) }
        until("the quick plugin got all three") { quick.seen.size == 3 }
        assertEquals(0, blocked.seen.size)
    }

    @Test fun eachPluginGetsItsEventsInOrder() = runBlocking {
        val p = Fake("ordered") { delay(5) }
        val rt = runtime()
        rt.register(listOf(p))
        rt.activate("ordered")
        val events = (1..20).map { WatchEvent.SettingChanged(w, "k$it", it) }
        events.forEach(rt::dispatch)
        until("all events") { p.seen.size == 20 }
        assertEquals(events, p.seen.toList())
    }

    @Test fun aThrowingPluginIsIsolatedLoggedOnceAndTurnedOffAfterRepeatedFailures() = runBlocking {
        val failed = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val bad = Fake("bad") { throw IllegalStateException("boom") }
        val good = Fake("good")
        val rt = runtime(PluginLimits(maxFailures = 3), failed, dropped)
        rt.register(listOf(bad, good))
        rt.activate("bad"); rt.activate("good")
        repeat(5) { rt.dispatch(WatchEvent.Disconnected(w)) }
        until("bad is turned off") { rt.state("bad") is PluginState.Failed }
        until("good got all five") { good.seen.size == 5 }
        assertEquals(PluginState.Active, rt.state("good"))
        assertEquals(listOf("bad"), failed)
        assertEquals("the failure is logged once", 1, lines.count { it.startsWith("bad: Disconnected failed") })
        assertTrue(lines.any { it == "bad: turned off after 3 failures in a row" })
        until("bad was deactivated and its commands dropped") { bad.deactivated == 1 && dropped == listOf("bad") }
        // Nothing reaches it any more.
        rt.dispatch(WatchEvent.Disconnected(w))
        until("good got the sixth") { good.seen.size == 6 }
    }

    @Test fun aPluginThatTakesTooLongCountsAsAFailure() = runBlocking {
        val slow = Fake("slow") { delay(10_000) }
        val rt = runtime(PluginLimits(eventTimeoutMs = 50, maxFailures = 2))
        rt.register(listOf(slow))
        rt.activate("slow")
        repeat(2) { rt.dispatch(WatchEvent.Disconnected(w)) }
        until("turned off") { rt.state("slow") is PluginState.Failed }
        assertTrue(lines.any { it.contains("took too long") })
    }

    @Test fun aPluginThatFailsToStartIsNotActive() = runBlocking {
        val p = object : Fake("nostart") { override suspend fun activate(host: PluginHost) = error("no") }
        val rt = runtime()
        rt.register(listOf(p))
        assertFalse(rt.activate("nostart"))
        assertTrue(rt.state("nostart") is PluginState.Failed)
    }

    @Test fun aStartThatHangsIsCutByTheTimeLimit() = runBlocking {
        val p = object : Fake("hang") { override suspend fun activate(host: PluginHost) = awaitCancellation() }
        val rt = runtime(PluginLimits(activateTimeoutMs = 50))
        rt.register(listOf(p))
        assertFalse(rt.activate("hang"))
    }

    @Test fun aPluginOfAnotherInterfaceVersionIsNotStarted() = runBlocking {
        val p = Fake("old", apiVersion = "9.0.0")
        val rt = runtime()
        rt.register(listOf(p))
        assertFalse(rt.activate("old"))
        assertEquals(PluginState.Incompatible("9.0.0"), rt.state("old"))
        assertEquals(null, p.host)
    }

    @Test fun deactivateDropsQueuedEventsAndCommands() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val dropped = mutableListOf<String>()
        val p = Fake("p") { gate.await() }
        val rt = runtime(dropped = dropped)
        rt.register(listOf(p))
        rt.activate("p")
        repeat(5) { rt.dispatch(WatchEvent.Disconnected(w)) }
        rt.deactivate("p")
        gate.complete(Unit)
        delay(100)
        assertEquals(0, p.seen.size)
        assertEquals(listOf("p"), dropped)
        assertEquals(PluginState.Off, rt.state("p"))
    }

    @Test fun buttonPressesGoOnlyToPluginsThatAskedForThem() = runBlocking {
        val asks = Fake("asks")
        val not = Fake("not")
        val rt = runtime()
        rt.register(listOf(asks, not))
        rt.activate("asks"); rt.activate("not")
        rt.declareNeeds("asks", PluginNeeds(buttonPress = true, values = setOf("watch.battery")))
        rt.declareNeeds("not", PluginNeeds(values = setOf("watch.temperature")))
        rt.dispatch(WatchEvent.ButtonPressed(w, java.time.Instant.EPOCH))
        rt.dispatch(WatchEvent.Disconnected(w))
        until("both got the disconnect") { asks.seen.size == 2 && not.seen.size == 1 }
        val needs = rt.needs()
        assertTrue(needs.buttonPress)
        assertEquals(setOf("watch.battery", "watch.temperature"), needs.values)
    }

    @Test fun theNeedsOfAPluginThatIsOffCountForNothing() = runBlocking {
        val p = Fake("p")
        val rt = runtime()
        rt.register(listOf(p))
        rt.activate("p")
        rt.declareNeeds("p", PluginNeeds(healthReads = true))
        assertTrue(rt.needs().healthReads)
        rt.deactivate("p")
        assertFalse(rt.needs().healthReads)
    }

    @Test fun aSecondPluginWithTheSameIdIsSkipped() {
        val rt = runtime()
        rt.register(listOf(Fake("same"), Fake("same"), Fake("Bad Id")))
        assertEquals(1, rt.plugins().size)
    }

    @Test fun aFullQueueDropsTheOldestStaleEventFirstWithOneLogLine() {
        val q = PluginEventQueue(3) { lines += it }
        val values = WatchEvent.ValuesUpdated(w, WatchValues(w), ValueCause.CONNECTION)
        q.offer(WatchEvent.Disconnected(w))
        q.offer(values)
        q.offer(WatchEvent.Connected(w, org.avmedia.smartsync.plugin.api.ConnectionKind.USER))
        q.offer(WatchEvent.ButtonPressed(w, java.time.Instant.EPOCH))
        assertEquals(3, q.size())
        val left = runBlocking { List(3) { q.take()!! } }
        // The values (a newer read replaces them) go; the older disconnect stays.
        assertEquals(listOf(WatchEvent.Disconnected::class, WatchEvent.Connected::class, WatchEvent.ButtonPressed::class), left.map { it::class })
        assertEquals(1, lines.size)
    }

    @Test fun aLongOverflowIsOneLogLine() {
        val q = PluginEventQueue(2) { lines += it }
        repeat(5) { q.offer(WatchEvent.SettingChanged(w, "k$it", it)) }
        q.offer(WatchEvent.Disconnected(w))
        assertEquals(2, q.size())
        assertEquals(1, lines.size)
    }
}
