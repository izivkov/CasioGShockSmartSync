package org.avmedia.gshockGoogleSync.plugins

import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.gshockGoogleSync.watchvalues.VariableContext
import org.avmedia.gshockGoogleSync.watchvalues.VariableRegistry
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.ValueInfo
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchValues

/**
 * The watch values for the plugins: one read of the [registry] (the keys the active plugins want), laid over the
 * values kept for that watch, kept across restarts in [store]. A value the watch did not give now keeps its older one.
 * Numbers are the same fresh and kept: a whole number is a Long, a fraction a Double ([normalise]). Android-free.
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
        val ctx = VariableContext(wireOf(cause), java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().withNano(0), connected)
        val fresh = registry().resolve(ctx, wanted).filterValues { it != null }.mapValues { normalise(it.value) }
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

    /** Every key the registry serves to plugins (those with a stated kind), in its order. */
    fun catalog(): List<ValueInfo> = registry().variables.mapNotNull { v ->
        v.meta?.let { m -> ValueInfo(v.name, v.description, m.use, m.type, m.unit) }
    }

    /** Every key [catalog] lists. */
    fun keys(): Set<String> = uses().keys

    /** The kind of data of [key]; null for a key the registry does not state one for (never served). */
    fun useOf(key: String): DataUse? = uses()[key]

    @Volatile private var usesCache: Map<String, DataUse>? = null

    private fun uses(): Map<String, DataUse> = usesCache ?: registry().variables.mapNotNull { v -> v.meta?.let { v.name to it.use } }.toMap().also { usesCache = it }

    companion object {
        const val KEY = "plugins.values"

        /** A value as the kept JSON gives it back: whole numbers as Long, fractions as Double, in lists and maps too. */
        fun normalise(v: Any?): Any? = when (v) {
            is Byte, is Short, is Int, is Long -> (v as Number).toLong()
            is Float -> v.toString().toDouble()
            is Double -> v
            is List<*> -> v.map(::normalise)
            is Map<*, *> -> v.entries.associate { (k, x) -> k.toString() to normalise(x) }
            else -> v
        }

        /** The word the "event" value carries for [cause] (the payload word of the values, kept as it was). */
        fun wireOf(cause: ValueCause): String = when (cause) {
            ValueCause.CONNECTION, ValueCause.REQUEST -> "sync"
            ValueCause.HEALTH_READ -> "health_data"
            ValueCause.WORKOUT -> "workout_downloaded"
            ValueCause.BUTTON -> "watch_action"
            ValueCause.SNAPSHOT -> "snapshot"
        }
    }
}
