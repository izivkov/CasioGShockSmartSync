package org.avmedia.gshockGoogleSync.plugins

import java.util.ServiceLoader
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The plugins the app ships: the three of smartsync-plugins, which start off. The old mobile app plugin is gone. */
class BundledPluginsTest {
    /** What the host finds at start (PluginHub), by class: no plugin is made. */
    private val found: Set<String> = ServiceLoader.load(WatchPlugin::class.java, WatchPlugin::class.java.classLoader)
        .stream().map { it.type().name }.toList().toSet()

    @Test fun theAppShipsTheThreePluginsOfSmartsyncPlugins() {
        assertEquals(
            setOf(
                "org.avmedia.smartsync.plugin.homeassistant.HomeAssistantPlugin",
                "org.avmedia.smartsync.plugin.healthconnect.HealthConnectPlugin",
                "org.avmedia.smartsync.plugin.trmnl.TrmnlPlugin",
            ),
            found,
        )
    }

    /** Settings > Plugins has one Home Assistant entry, the MQTT plugin, under that name. */
    @Test fun thePluginListHasOneHomeAssistantEntry() {
        val plugins = ServiceLoader.load(WatchPlugin::class.java, WatchPlugin::class.java.classLoader).toList()
        assertEquals(plugins.map { it.name }.toString(), plugins.size, plugins.map { it.name }.toSet().size)
        assertEquals(plugins.size, plugins.map { it.id }.toSet().size)
        val ha = plugins.filter { it.name.contains("Home Assistant") }
        assertEquals(listOf("Home Assistant"), ha.map { it.name })
        assertEquals("home_assistant_mqtt", ha.single().id)
        assertFalse(plugins.any { it.id == "home_assistant" })
    }

    /** A phone where the old plugin was set up: nothing starts by itself (its data stays until deleted). */
    @Test fun everyPluginStartsOffEvenWhereTheOldPluginWasSetUp() {
        val kv = Mem().apply { m["ha.config"] = """{"baseUrl":"https://ha.example","token":"t"}"""; m["toggle.home_assistant"] = "on" }
        val toggles = PluginToggles(kv)
        listOf("home_assistant_mqtt", "health_connect", "trmnl").forEach { assertFalse(it, toggles.isOn(it, default = false)) }
    }

    private class Mem : org.avmedia.gshockGoogleSync.watch.KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }
}
