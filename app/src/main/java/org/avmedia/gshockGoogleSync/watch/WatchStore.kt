package org.avmedia.gshockGoogleSync.watch

import com.google.gson.Gson

/**
 * Every value the app keeps about a watch, kept per watch ([WatchKeys]) with the time it was stored, as a
 * [StoredValue]. Values kept by older versions without a watch (one watch only) belong to one watch, the legacy owner:
 * they move to its key the first time it reads them, once. Android-free.
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
        kv.put(WatchKeys.scoped(name, watch), gson.toJson(Envelope(value, atMs)))
    }

    /** Several values of [watch] in one store write; a null value deletes its name. */
    fun putAll(watch: String, values: Map<String, String?>, atMs: Long = clock()) {
        if (watch.isBlank()) return
        kv.putAll(values.entries.associate { (name, v) -> WatchKeys.scoped(name, watch) to v?.let { gson.toJson(Envelope(it, atMs)) } })
    }

    /**
     * The value of [name] for [watch]; null for none. With [legacyKey], a value an older version kept there (for one
     * watch only) becomes the legacy owner's, once; any other watch never sees it.
     */
    fun get(watch: String, name: String, legacyKey: String? = null): StoredValue<String>? {
        if (watch.isBlank()) return null
        kv.get(WatchKeys.scoped(name, watch))?.let { raw ->
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
        if (watch.isNotBlank()) kv.delete(WatchKeys.scoped(name, watch))
    }

    /** The watch the values of older versions belong to, chosen once: [pickLegacyOwner], else the first that asks. */
    fun legacyOwner(asking: String): String =
        kv.get(KEY_LEGACY_OWNER)?.takeIf { it.isNotBlank() }
            ?: (runCatching { pickLegacyOwner() }.getOrNull()?.takeIf { it.isNotBlank() } ?: asking).also { kv.put(KEY_LEGACY_OWNER, it) }

    fun isLegacyOwner(watch: String): Boolean = watch.isNotBlank() && legacyOwner(watch) == watch

    /** A re-paired watch has a new key: every value of [from] (and the legacy ownership) goes to [to]. */
    fun move(from: String, to: String) {
        if (from.isBlank() || to.isBlank() || from == to) return
        for (key in kv.keys().filter { WatchKeys.belongsTo(it, from) }) {
            val name = key.removeSuffix("@$from")
            kv.get(key)?.let { kv.put(WatchKeys.scoped(name, to), it) }
            kv.delete(key)
        }
        if (kv.get(KEY_LEGACY_OWNER) == from) kv.put(KEY_LEGACY_OWNER, to)
    }

    companion object {
        const val KEY_LEGACY_OWNER = "watch.legacy_owner"
    }
}
