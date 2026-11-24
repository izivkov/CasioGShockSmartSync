package org.avmedia.gshockGoogleSync.plugins

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.gshockGoogleSync.watchvalues.VariableContext
import org.avmedia.gshockGoogleSync.watchvalues.VariableRegistry
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchValues

/**
 * The watch values for the plugins: one read of the [registry] (the keys the active plugins want), laid over the
 * values kept for that watch, kept across restarts in [store]. A value the watch did not give now keeps its older one.
 * Android-free.
 */
class WatchValuesHost(
    private val registry: () -> VariableRegistry,
    private val store: WatchStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).serializeNulls().create()

    private class Kept(val values: Map<String, Any?>? = emptyMap(), val at: Map<String, Any?>? = emptyMap())

    /** Reads [wanted] (null = every key) of [watch]; [connected] false skips what needs the watch. */
    suspend fun read(watch: WatchId, cause: ValueCause, wanted: Set<String>?, connected: Boolean): WatchEvent.ValuesUpdated {
        val now = clock()
        val ctx = VariableContext(cause.wire, java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().withNano(0), connected)
        val fresh = registry().resolve(ctx, wanted).filterValues { it != null }
        val old = kept(watch)
        val merged = WatchValues(watch, old.values + fresh, old.readAtMillis + fresh.keys.associateWith { now })
        runCatching { store.put(watch.key, KEY, gson.toJson(Kept(merged.values, merged.readAtMillis))) }
        return WatchEvent.ValuesUpdated(watch, merged, cause, fresh.keys)
    }

    /** The values kept for [watch]; empty before its first read. */
    fun kept(watch: WatchId): WatchValues {
        val raw = store.get(watch.key, KEY)?.value ?: return WatchValues(watch)
        val k = runCatching { gson.fromJson(raw, Kept::class.java) }.getOrNull() ?: return WatchValues(watch)
        return WatchValues(watch, k.values.orEmpty(), k.at.orEmpty().mapNotNull { (key, v) -> (v as? Number)?.let { key to it.toLong() } }.toMap())
    }

    /** The keys the registry knows, in its order. */
    fun keys(): List<String> = registry().variables.map { it.name }

    companion object {
        const val KEY = "plugins.values"
    }
}
