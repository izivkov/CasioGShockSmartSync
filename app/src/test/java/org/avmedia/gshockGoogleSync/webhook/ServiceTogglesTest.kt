package org.avmedia.gshockGoogleSync.webhook

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
    fun bothAreOnByDefault() {
        val t = ServiceToggles(Mem())
        ServiceToggles.Service.values().forEach { assertTrue(it.name, t.isOn(it)) }
    }

    @Test
    fun offIsKeptAndEachServiceIsIndependent() {
        val mem = Mem()
        ServiceToggles(mem).set(ServiceToggles.Service.WEBHOOKS, false)
        val again = ServiceToggles(mem)
        assertFalse(again.isOn(ServiceToggles.Service.WEBHOOKS))
        assertTrue(again.isOn(ServiceToggles.Service.HOME_ASSISTANT))
        again.set(ServiceToggles.Service.WEBHOOKS, true)
        assertTrue(ServiceToggles(mem).isOn(ServiceToggles.Service.WEBHOOKS))
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
