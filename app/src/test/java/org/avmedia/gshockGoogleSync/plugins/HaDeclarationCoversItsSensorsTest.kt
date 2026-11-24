package org.avmedia.gshockGoogleSync.plugins

import io.mockk.mockk
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import org.avmedia.smartsync.homeassistant.HaRuntime
import org.avmedia.smartsync.homeassistant.HaSensors
import org.avmedia.smartsync.homeassistant.HomeAssistantPlugin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The in-repo Home Assistant plugin keeps every sensor of the owner's setup: each value one of its sensors reads has a
 * kind (the host's registry) that the plugin declared, so the host serves all of them.
 */
class HaDeclarationCoversItsSensorsTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    @Test fun everySensorValueIsServedToHomeAssistant() {
        val r = WatchVariables.registry(
            WatchVariables.Env(api = mockk(relaxed = true), features = mockk(relaxed = true), raw = mockk(relaxed = true), isH5600 = { true }, phoneBattery = { 50 }, lastSync = { null }),
        )
        val host = WatchValuesHost({ r }, WatchStore(Mem()))
        val access = HomeAssistantPlugin().access
        val keys = HaSensors.variablesOf(HaSensors.ALL) - HaRuntime.PLUGIN_VALUES
        assertEquals("not served to Home Assistant", emptyList<String>(), keys.filter { !AccessGuard.mayRead(access, host::useOf, it) }.sorted())
    }
}
