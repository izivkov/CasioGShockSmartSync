package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockGoogleSync.watch.KeyValueStore

/** The paired watches the host lists for plugins ([PluginHub.watches]), by watch key. Android-free. */
object PairedWatchList {
    private const val MOVED = "host.moved."

    /**
     * The paired [keys] to list: not one whose data moved to its re-paired id ([moved]), unless that watch is the
     * connected one (it is still paired under the old address and connected under it).
     */
    fun visible(keys: List<String>, connected: String?, kv: KeyValueStore): List<String> =
        keys.distinct().filter { it == connected || runCatching { kv.get(MOVED + it) }.getOrNull() == null }

    /** [from]'s data moved to [to] (a re-pair). */
    fun moved(kv: KeyValueStore, from: String, to: String) {
        runCatching { kv.put(MOVED + from, to) }
    }

    /** [key] connected under its own id: it is a watch of its own again. */
    fun seen(kv: KeyValueStore, key: String) {
        runCatching { kv.delete(MOVED + key) }
    }
}
