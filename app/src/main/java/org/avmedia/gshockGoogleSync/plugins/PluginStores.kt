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
     * Their layout predates the key rule ([StorageRules]), so it is not applied to them.
     */
    private val KEPT = mapOf("home_assistant" to "ha.")

    fun prefixOf(pluginId: String): String = KEPT[pluginId] ?: "plugin.$pluginId."

    fun isKept(pluginId: String): Boolean = pluginId in KEPT

    /** The per-watch values of a plugin always start with "plugin.": [WatchStorage.deleteAll] finds them. */
    fun perWatchPrefixOf(pluginId: String): String = prefixOf(pluginId).takeIf { it.startsWith("plugin.") } ?: "plugin.$pluginId."

    /** Every key of plugin [id]: its store, its per-watch values and the host's own keys about it (switch, failure). */
    fun ownsKey(id: String, key: String): Boolean =
        key.startsWith(prefixOf(id)) || key.startsWith(perWatchPrefixOf(id)) || key == "toggle.$id" || key.startsWith("plugins.$id.")

    /** Plugin ids that still have data in [keys] but are not in [present] (a plugin no longer in this build). */
    fun leftOver(keys: Set<String>, present: Set<String>): Set<String> =
        keys.filter { it.startsWith("plugin.") }.map { it.removePrefix("plugin.").substringBefore('.') }
            .filter { it.isNotBlank() && it !in present }.toSet()
}

/** The rules of [PluginStorage]: key characters and the bound per plugin. Android-free. */
object StorageRules {
    const val MAX_KEYS = 2000
    const val MAX_VALUE_CHARS = 64 * 1024
    private val KEY = Regex("[A-Za-z0-9._-]{1,200}")

    fun keyOk(key: String): Boolean = KEY.matches(key)
}

/**
 * A plugin's store for all watches: its keys under [prefix]. No call throws (a store error is logged); a refused key
 * or a store over its bound is one log line and nothing written. [checked] false: a kept layout ([PluginNamespaces]).
 */
class NamespacedStorage(
    private val kv: KeyValueStore,
    private val prefix: String,
    private val checked: Boolean = true,
    private val log: (String) -> Unit = {},
) : PluginStorage {
    override fun get(key: String): String? = quietly(null) { kv.get(prefix + key) }
    override fun put(key: String, value: String) = putAll(mapOf(key to value))
    override fun delete(key: String) = quietly(Unit) { kv.delete(prefix + key) }
    override fun keys(): Set<String> = quietly(emptySet()) { kv.keys().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet() }

    override fun putAll(values: Map<String, String?>) = quietly(Unit) {
        if (checked && !allowed(values, keys())) return@quietly
        kv.putAll(values.mapKeys { prefix + it.key })
    }

    private fun allowed(values: Map<String, String?>, existing: Set<String>): Boolean {
        values.keys.firstOrNull { !StorageRules.keyOk(it) }?.let { log("storage: key refused (only A-Z, a-z, 0-9, '.', '_', '-')"); return false }
        values.entries.firstOrNull { (it.value?.length ?: 0) > StorageRules.MAX_VALUE_CHARS }?.let { log("storage: value of ${it.key} over ${StorageRules.MAX_VALUE_CHARS} characters, not written"); return false }
        val after = existing + values.filterValues { it != null }.keys - values.filterValues { it == null }.keys
        if (after.size > StorageRules.MAX_KEYS) { log("storage: over ${StorageRules.MAX_KEYS} keys, not written"); return false }
        return true
    }

    private inline fun <T> quietly(fallback: T, block: () -> T): T = try { block() } catch (e: Exception) {
        log("storage failed (${e.javaClass.simpleName})"); fallback
    }
}

/** A plugin's store for one watch: "prefix+key@watch", moved with the watch ([WatchStore.move]) and deleted with it. */
class WatchStorage(
    private val kv: KeyValueStore,
    private val watches: WatchStore,
    private val prefix: String,
    private val watch: String,
    private val log: (String) -> Unit = {},
) : PluginStorage {
    override fun get(key: String): String? = quietly(null) { watches.get(watch, prefix + key)?.value }
    override fun put(key: String, value: String) = putAll(mapOf(key to value))
    override fun delete(key: String) = quietly(Unit) { watches.delete(watch, prefix + key) }
    override fun keys(): Set<String> = quietly(emptySet()) {
        kv.keys().filter { it.startsWith(prefix) && WatchKeys.belongsTo(it, watch) }
            .map { it.removePrefix(prefix).removeSuffix("@$watch") }.toSet()
    }

    override fun putAll(values: Map<String, String?>) = quietly(Unit) {
        if (values.keys.any { !StorageRules.keyOk(it) }) { log("storage: key refused (only A-Z, a-z, 0-9, '.', '_', '-')"); return@quietly }
        if (values.values.any { (it?.length ?: 0) > StorageRules.MAX_VALUE_CHARS }) { log("storage: value over ${StorageRules.MAX_VALUE_CHARS} characters, not written"); return@quietly }
        if ((keys() + values.filterValues { it != null }.keys).size > StorageRules.MAX_KEYS) { log("storage: over ${StorageRules.MAX_KEYS} keys, not written"); return@quietly }
        watches.putAll(watch, values.mapKeys { prefix + it.key })
    }

    private inline fun <T> quietly(fallback: T, block: () -> T): T = try { block() } catch (e: Exception) {
        log("storage failed (${e.javaClass.simpleName})"); fallback
    }

    companion object {
        /** Every plugin's per-watch values of [watch] (plugin keys only, never the app's own). */
        fun deleteAll(kv: KeyValueStore, watch: String) {
            if (watch.isBlank()) return
            kv.keys().filter { it.startsWith("plugin.") && WatchKeys.belongsTo(it, watch) }.forEach(kv::delete)
        }
    }
}

/** What the host keeps per watch for the plugins, beyond their own stores. Android-free. */
object PluginWatchData {
    /** The host's own per-watch keys: capabilities at the last connection, the kept values. */
    val HOST_NAMES = listOf(PluginHub.KEY_CAPS, WatchValuesHost.KEY)

    /** The watches the host saw at a connection (and that were not removed since): the candidates of [RePair]. */
    fun known(kv: KeyValueStore): List<String> = kv.keys().filter { it.startsWith("${PluginHub.KEY_CAPS}@") }.map { it.substringAfter('@') }

    /** A removed watch: every plugin's values of it and the host's own, so it is never a re-pair candidate again. */
    fun remove(kv: KeyValueStore, watch: String) {
        if (watch.isBlank()) return
        WatchStorage.deleteAll(kv, watch)
        HOST_NAMES.forEach { kv.delete(WatchKeys.scoped(it, watch)) }
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
