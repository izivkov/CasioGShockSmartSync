package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTogglesTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    @Test
    fun onByDefault() {
        val t = ServiceToggles(Mem())
        ServiceToggles.Service.values().forEach { assertTrue(it.name, t.isOn(it)) }
    }

    @Test
    fun offIsKept() {
        val mem = Mem()
        ServiceToggles(mem).set(ServiceToggles.Service.HOME_ASSISTANT, false)
        val again = ServiceToggles(mem)
        assertFalse(again.isOn(ServiceToggles.Service.HOME_ASSISTANT))
        again.set(ServiceToggles.Service.HOME_ASSISTANT, true)
        assertTrue(ServiceToggles(mem).isOn(ServiceToggles.Service.HOME_ASSISTANT))
    }

    @Test
    fun turningHomeAssistantOffKeepsTheRegistration() {
        val mem = Mem()
        val ha = HaStore(mem)
        ha.saveRegistration("gshock-1", HaRegistration("hook1", "{}"))
        ServiceToggles(mem).set(ServiceToggles.Service.HOME_ASSISTANT, false)
        ServiceToggles(mem).set(ServiceToggles.Service.HOME_ASSISTANT, true)
        assertEquals("hook1", ha.loadRegistration("gshock-1")?.webhookId)
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
