package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockGoogleSync.utils.RemovedFeatureCleanup
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.smartsync.plugin.api.PluginStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each plugin's switch, storage and health, on the app's store. */
class PluginStoresTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    @Test fun aPluginIsOnByDefaultOnlyWhenItSaysSo() {
        val t = PluginToggles(Mem())
        assertTrue(t.isOn("home_assistant", default = true))
        assertFalse(t.isOn("health_connect", default = false))
    }

    @Test fun homeAssistantKeepsItsSwitchKeyAndOffIsKept() {
        val mem = Mem()
        // The Home Assistant switch of versions before the plugin interface.
        mem.m["toggle.home_assistant"] = "off"
        assertFalse(PluginToggles(mem).isOn("home_assistant", default = true))
        PluginToggles(mem).set("home_assistant", true)
        assertTrue(PluginToggles(mem).isOn("home_assistant", default = true))
    }

    @Test fun aPluginTurnedOffAfterFailuresStaysOffUntilSwitchedOn() {
        val mem = Mem()
        val t = PluginToggles(mem)
        t.markFailed("p", "turned off after 5 failures in a row")
        assertFalse(t.isOn("p", default = true))
        t.set("p", true)
        assertTrue(t.isOn("p", default = true))
        assertNull(t.failedReason("p"))
    }

    @Test fun homeAssistantReadsItsKeysWhereTheyWere() {
        val mem = Mem()
        mem.m["ha.config"] = "{}"
        val s = NamespacedStorage(mem, PluginNamespaces.prefixOf("home_assistant"))
        assertEquals("{}", s.get("config"))
        s.put("regs", "[]")
        assertEquals("[]", mem.m["ha.regs"])
        assertEquals(setOf("config", "regs"), s.keys())
        assertEquals("plugin.trmnl.", PluginNamespaces.prefixOf("trmnl"))
    }

    @Test fun aWatchsPluginValuesMoveWithItAndGoWithIt() {
        val mem = Mem()
        val watches = WatchStore(mem)
        val a = WatchStorage(mem, watches, "plugin.p.", "gshock-aaaaaaaaaa01")
        a.put("sent_hours", "10,11")
        assertEquals(setOf("sent_hours"), a.keys())
        watches.move("gshock-aaaaaaaaaa01", "gshock-bbbbbbbbbb02")
        assertEquals("10,11", WatchStorage(mem, watches, "plugin.p.", "gshock-bbbbbbbbbb02").get("sent_hours"))
        mem.m["app.lastSync@gshock-bbbbbbbbbb02"] = "x"
        WatchStorage.deleteAll(mem, "gshock-bbbbbbbbbb02")
        assertNull(WatchStorage(mem, watches, "plugin.p.", "gshock-bbbbbbbbbb02").get("sent_hours"))
        assertEquals("the app's own values stay", "x", mem.m["app.lastSync@gshock-bbbbbbbbbb02"])
    }

    @Test fun theStatusShowsTheLastErrorAndClearsOnSuccess() {
        var now = 1_000L
        val b = PluginHealthBoard { now }
        b.reported("p", PluginStatus.Error("server not reachable"))
        assertEquals(PluginHealth("server not reachable", 1_000L, 1), b.of("p"))
        now = 2_000L
        b.caught("p", "onEvent failed (IllegalStateException)")
        assertEquals("the newest error shows", "onEvent failed (IllegalStateException)", b.of("p").lastError)
        assertEquals(2, b.of("p").failures)
        b.callbackOk("p")
        assertEquals("the plugin's own error stays until it reports Ok", "server not reachable", b.of("p").lastError)
        b.reported("p", PluginStatus.Ok())
        assertEquals(PluginHealth(), b.of("p"))
        assertEquals("Last error: x, 14:05 (2 failures)", PluginHealthBoard.line(PluginHealth("x", 0, 2)) { "14:05" })
        assertNull(PluginHealthBoard.line(PluginHealth()) { "" })
    }
}

class RemovedFeatureCleanupTest {
    @Test
    fun dropsOnlyWhatTheRemovedFeatureStored() {
        listOf("webhooks.list", "webhooks.draft", "webhooks.results", "webhooks.pending.abc", "webhooks.lastAttempt.abc",
            "webhooks.list.unreadable.1700000000000", "webhooks.lastSync", "toggle.webhooks", "WebhookAction.enabled",
        ).forEach { assertTrue(it, RemovedFeatureCleanup.isRemoved(it)) }
        listOf("toggle.home_assistant", "ha.config", "ha.registration.gshock-1", "raw.read.until", "app.lastSync",
        ).forEach { assertFalse(it, RemovedFeatureCleanup.isRemoved(it)) }
    }
}
