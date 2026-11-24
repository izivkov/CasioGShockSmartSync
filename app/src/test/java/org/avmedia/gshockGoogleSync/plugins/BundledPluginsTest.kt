package org.avmedia.gshockGoogleSync.plugins

import java.util.ServiceLoader
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plugins the app ships: its own Home Assistant plugin and the three of smartsync-plugins, which start off. */
class BundledPluginsTest {
    /** What the host finds at start (PluginHub), by class: no plugin is made. */
    private val found: Set<String> = ServiceLoader.load(WatchPlugin::class.java, WatchPlugin::class.java.classLoader)
        .stream().map { it.type().name }.toList().toSet()

    @Test fun theAppShipsItsOwnHomeAssistantPluginAndTheThreeOfSmartsyncPlugins() {
        assertEquals(
            setOf(
                "org.avmedia.smartsync.homeassistant.HomeAssistantPlugin",
                "org.avmedia.smartsync.plugin.homeassistant.HomeAssistantPlugin",
                "org.avmedia.smartsync.plugin.healthconnect.HealthConnectPlugin",
                "org.avmedia.smartsync.plugin.trmnl.TrmnlPlugin",
            ),
            found,
        )
    }

    /** Settings > Plugins tells them apart: two of them are Home Assistant plugins. */
    @Test fun eachHasItsOwnNameAndId() {
        val plugins = ServiceLoader.load(WatchPlugin::class.java, WatchPlugin::class.java.classLoader).toList()
        assertEquals(plugins.map { it.name }.toString(), plugins.size, plugins.map { it.name }.toSet().size)
        assertEquals(plugins.size, plugins.map { it.id }.toSet().size)
    }

    @Test fun theThreeStartOffEvenWhereHomeAssistantWasSetUpBefore() {
        val kv = Mem().apply { m["ha.config"] = """{"baseUrl":"https://ha.example","token":"t"}""" }
        assertTrue("the app's own plugin keeps its switch", PluginHub.legacyOn("home_assistant", kv))
        listOf("home_assistant_mqtt", "health_connect", "trmnl").forEach { assertFalse(it, PluginHub.legacyOn(it, kv)) }
    }

    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }
}
