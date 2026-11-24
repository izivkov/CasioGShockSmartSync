package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockGoogleSync.watch.KeyValueStore

/** The paired watches the host lists for plugins ([PluginHub.watches]), by watch key. Android-free. */
object PairedWatchList {
    private const val MOVED = "host.moved."

    /** The paired [keys] to list. */
    fun visible(keys: List<String>, connected: String?, kv: KeyValueStore): List<String> = keys.distinct()

    /** [from]'s data moved to [to] (a re-pair). */
    fun moved(kv: KeyValueStore, from: String, to: String) {
        runCatching { kv.put(MOVED + from, to) }
    }

    /** [key] connected under its own id: it is a watch of its own again. */
    fun seen(kv: KeyValueStore, key: String) {
        runCatching { kv.delete(MOVED + key) }
    }
}
