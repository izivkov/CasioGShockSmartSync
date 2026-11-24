package org.avmedia.gshockGoogleSync.plugins

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.Delivered
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.NotificationKind
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchNotification
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A plugin's host object: revoked after stop (B3), refresh (H5), all-or-nothing settings (S5), history bounds. */
class AppPluginHostTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val w = WatchId("gshock-aabbccddeeff")

    @After fun tearDown() = scope.cancel()

    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private class P(override val access: PluginAccess) : WatchPlugin {
        override val id = "p"
        override val name = "P"
        override val version = "1"
        override val apiVersion = PluginApi.VERSION
        override suspend fun activate(host: PluginHost) {}
        override suspend fun deactivate() {}
        override suspend fun onEvent(event: WatchEvent) {}
    }

    private val all = PluginAccess(reads = DataUse.entries.toSet(), controls = Control.entries.toSet())
    private val kv = Mem()

    private fun hub(connected: Boolean = true): PluginHub = mockk<PluginHub>(relaxed = true).also { h ->
        every { h.scheduler } returns CommandScheduler(scope, connected = { connected }, transferRunning = { false })
        every { h.runtime } returns PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> })
        every { h.capsOf(any()) } returns emptySet()
        every { h.connectedWatch() } returns if (connected) w else null
        every { h.watchName(any()) } returns "CASIO GBX-H5600"
        every { h.showNotification(any(), any()) } returns CommandScheduler.DONE
        every { h.kv } returns kv
        every { h.knowsWatch(any()) } returns true
    }

    private val note = WatchNotification(NotificationKind.GENERIC, "app", "t", "x")

    @Test fun aRevokedHostWritesNothingAndSaysStopped() = runBlocking {
        val h = hub()
        val host = AppPluginHost(h, P(all))
        assertEquals(PluginResult.Ok(Delivered.NOW), host.notify(w, note))
        host.revoke()
        val r = host.notify(w, note) as PluginResult.Failed
        assertEquals(FailReason.STOPPED, r.reason)
        assertEquals(FailReason.STOPPED, (host.command(w, WatchCommand.RefreshValues) as PluginResult.Failed).reason)
        verify(exactly = 1) { h.showNotification(any(), any()) }
        assertEquals("reads give nothing", emptyList<Any>(), host.watches())
        assertEquals(null, host.connected())
        host.storage().put("token", "t")
        assertEquals("its storage still works (its screen may run while it is off)", "t", host.storage().get("token"))
    }

    @Test fun aRefreshGivesTheReadsResultAndKeepsToDelivery() = runBlocking {
        val h = hub()
        coEvery { h.refreshValues() } returns PluginResult.Failed(FailReason.TIMEOUT, "no answer")
        val host = AppPluginHost(h, P(all))
        assertEquals("the read's own result", FailReason.TIMEOUT, (host.command(w, WatchCommand.RefreshValues) as PluginResult.Failed).reason)
        val away = AppPluginHost(hub(connected = false), P(all))
        assertEquals(CommandScheduler.QUEUED, away.command(w, WatchCommand.RefreshValues, Delivery.NextConnection()))
        assertEquals(FailReason.NOT_CONNECTED, (away.command(w, WatchCommand.RefreshValues) as PluginResult.Failed).reason)
        val nothing = AppPluginHost(hub(), P(PluginAccess()))
        assertEquals("no data declared: no reads", FailReason.NOT_DECLARED, (nothing.command(w, WatchCommand.RefreshValues) as PluginResult.Failed).reason)
    }

    @Test fun settingsAreWrittenAllOrNothing() = runBlocking {
        val h = hub()
        val host = AppPluginHost(h, P(all))
        val unknown = host.command(w, WatchCommand.ChangeSettings(mapOf("step_goal" to 8000, "nope" to 1))) as PluginResult.Failed
        assertEquals(FailReason.NOT_SUPPORTED, unknown.reason)
        assertTrue(unknown.message.startsWith("nope:"))
        val bad = host.command(w, WatchCommand.ChangeSettings(mapOf("step_goal" to "lots"))) as PluginResult.Failed
        assertEquals(FailReason.INVALID, bad.reason)
        io.mockk.coVerify(exactly = 0) { h.writeSettings(any(), any(), any()) }
    }

    @Test fun historyRefusesALongRangeAndAnUnknownWatch() = runBlocking {
        val h = hub()
        val host = AppPluginHost(h, P(all))
        val today = java.time.LocalDate.of(2026, 11, 24)
        val long = host.history(w, DataKind.Days, HistoryRange.days(today.minusDays(401), today)) as PluginResult.Failed
        assertEquals(FailReason.INVALID, long.reason)
        every { h.knowsWatch(any()) } returns false
        assertEquals(FailReason.INVALID, (host.history(w, DataKind.Days, HistoryRange.days(today, today)) as PluginResult.Failed).reason)
    }
}
