package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchKeys
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.smartsync.plugin.api.PluginStorage

/** Where each plugin's keys live in the app's store. Android-free. */
object PluginNamespaces {
    /**
     * Plugins that existed inside the app before the plugin interface keep their keys where they were, so a stored
     * setup (Home Assistant: URL, token, registrations, per-watch values) is read as it is, with nothing to migrate.
     */
    private val KEPT = mapOf("home_assistant" to "ha.")

    fun prefixOf(pluginId: String): String = KEPT[pluginId] ?: "plugin.$pluginId."
}

/** A plugin's store for all watches: its keys under [prefix]. */
class NamespacedStorage(private val kv: KeyValueStore, private val prefix: String) : PluginStorage {
    override fun get(key: String): String? = kv.get(prefix + key)
    override fun put(key: String, value: String) = kv.put(prefix + key, value)
    override fun delete(key: String) = kv.delete(prefix + key)
    override fun keys(): Set<String> = kv.keys().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
}

/** A plugin's store for one watch: "prefix+key@watch", moved with the watch ([WatchStore.move]) and deleted with it. */
class WatchStorage(
    private val kv: KeyValueStore,
    private val watches: WatchStore,
    private val prefix: String,
    private val watch: String,
) : PluginStorage {
    override fun get(key: String): String? = watches.get(watch, prefix + key)?.value
    override fun put(key: String, value: String) = watches.put(watch, prefix + key, value)
    override fun delete(key: String) = watches.delete(watch, prefix + key)
    override fun keys(): Set<String> =
        kv.keys().filter { it.startsWith(prefix) && WatchKeys.belongsTo(it, watch) }
            .map { it.removePrefix(prefix).removeSuffix("@$watch") }.toSet()

    companion object {
        /** Every plugin's per-watch values of [watch] (plugin keys only, never the app's own). */
        fun deleteAll(kv: KeyValueStore, watch: String) {
            if (watch.isBlank()) return
            kv.keys().filter { it.startsWith("plugin.") && WatchKeys.belongsTo(it, watch) }.forEach(kv::delete)
        }
    }
}

/**
 * Each plugin's on/off switch ("toggle.<id>": Home Assistant's switch already had that key), the reason the app turned
 * it off after failures (it stays off until switched on), and whether the person saw what it uses before turning it on.
 */
class PluginToggles(private val kv: KeyValueStore) {
    fun isOn(id: String, default: Boolean): Boolean = when (kv.get(key(id))) {
        "on" -> failedReason(id) == null
        "off" -> false
        else -> default && failedReason(id) == null
    }

    fun set(id: String, on: Boolean) {
        kv.put(key(id), if (on) "on" else "off")
        if (on) kv.delete(failedKey(id))
    }

    fun markFailed(id: String, reason: String) = kv.put(failedKey(id), reason)

    fun failedReason(id: String): String? = kv.get(failedKey(id))?.takeIf { it.isNotBlank() }

    fun accessShown(id: String): Boolean = kv.get("plugins.$id.access_shown") == "yes"

    fun markAccessShown(id: String) = kv.put("plugins.$id.access_shown", "yes")

    private fun key(id: String) = "toggle.$id"
    private fun failedKey(id: String) = "plugins.$id.failed"
}
