package org.avmedia.smartsync.homeassistant

import com.google.gson.Gson
import org.avmedia.smartsync.plugin.api.PluginStorage

/** A string store: the plugin's storage from the host on the phone, a map in unit tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)

    /** Every key in the store; per-watch values move with it when a watch is re-paired ([WatchStore.move]). */
    fun keys(): Set<String> = emptySet()
}

/** The host's storage of this plugin as a [KeyValueStore]. */
class PluginKeyValueStore(private val storage: PluginStorage) : KeyValueStore {
    override fun get(key: String): String? = storage.get(key)
    override fun put(key: String, value: String) = storage.put(key, value)
    override fun delete(key: String) = storage.delete(key)
    override fun keys(): Set<String> = storage.keys()
}

/** A value kept for one watch and when it was stored (epoch millis, 0 = not known). */
data class StoredValue(val value: String, val watch: String, val atMs: Long)

/**
 * The Home Assistant values kept per watch, under "name@watch" with the time they were stored (the format the app used
 * before the plugin, so stored values stay readable). Values of versions without per-watch values (one watch only)
 * belong to one watch, the legacy owner: they move to its key the first time it reads them, once. Android-free.
 */
class WatchStore(
    private val kv: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The watch the values of older versions belong to (the registered GBX); null = the first watch that reads. */
    private val pickLegacyOwner: () -> String? = { null },
) {
    private val gson = Gson()

    private class Envelope(val v: String? = null, val at: Long = 0)

    fun put(watch: String, name: String, value: String, atMs: Long = clock()) {
        if (watch.isBlank()) return
        kv.put(scoped(name, watch), gson.toJson(Envelope(value, atMs)))
    }

    /** The value of [name] for [watch]; null for none. With [legacyKey], an older version's value becomes the legacy owner's, once. */
    fun get(watch: String, name: String, legacyKey: String? = null): StoredValue? {
        if (watch.isBlank()) return null
        kv.get(scoped(name, watch))?.let { raw ->
            val e = runCatching { gson.fromJson(raw, Envelope::class.java) }.getOrNull() ?: return null
            return e.v?.let { StoredValue(it, watch, e.at) }
        }
        if (legacyKey == null) return null
        val old = kv.get(legacyKey) ?: return null
        if (!isLegacyOwner(watch)) return null
        put(watch, name, old, 0)
        kv.delete(legacyKey)
        return StoredValue(old, watch, 0)
    }

    fun delete(watch: String, name: String) {
        if (watch.isNotBlank()) kv.delete(scoped(name, watch))
    }

    fun legacyOwner(asking: String): String =
        kv.get(KEY_LEGACY_OWNER)?.takeIf { it.isNotBlank() }
            ?: (runCatching { pickLegacyOwner() }.getOrNull()?.takeIf { it.isNotBlank() } ?: asking).also { kv.put(KEY_LEGACY_OWNER, it) }

    fun isLegacyOwner(watch: String): Boolean = watch.isNotBlank() && legacyOwner(watch) == watch

    /** A re-paired watch has a new key: every Home Assistant value of [from] goes to [to]. */
    fun move(from: String, to: String) {
        if (from.isBlank() || to.isBlank() || from == to) return
        for (key in kv.keys().filter { it.endsWith("@$from") }) {
            val name = key.removeSuffix("@$from")
            kv.get(key)?.let { kv.put(scoped(name, to), it) }
            kv.delete(key)
        }
        if (kv.get(KEY_LEGACY_OWNER) == from) kv.put(KEY_LEGACY_OWNER, to)
    }

    companion object {
        const val KEY_LEGACY_OWNER = "legacy_owner"

        fun scoped(name: String, watch: String): String = "$name@$watch"
    }
}

/** Where a line comes from inside the plugin (all of them go out under the plugin's tag). */
enum class LogSource { HomeAssistant, App }

/**
 * The plugin's App log lines: Timber with the plugin's id as tag ([TAG]); the app writes info and above to its App log.
 * Never a token, webhook id, URL with a path or a notification's text: [org.avmedia.smartsync.plugin.api.Redact].
 */
object AppLog {
    const val TAG = HomeAssistantPlugin.ID

    fun log(source: LogSource, text: String) = timber.log.Timber.tag(TAG).i(text)

    /** [text] and the exception class only (its message can hold a URL or a value). */
    fun failure(source: LogSource, text: String, t: Throwable? = null) =
        timber.log.Timber.tag(TAG).w(if (t == null) text else "$text (${t.javaClass.simpleName})")

    fun debug(text: String) = timber.log.Timber.tag(TAG).d(text)
}

/** Turns a failure into a short line for the screens and the app log. Never includes URLs or headers. */
object Diagnostics {
    /** "ClassName: message" */
    fun oneLine(t: Throwable): String = t.javaClass.simpleName + (t.message?.let { ": ${it.take(160)}" } ?: "")
}
