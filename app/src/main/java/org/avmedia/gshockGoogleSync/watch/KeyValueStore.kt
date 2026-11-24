package org.avmedia.gshockGoogleSync.watch

/** A string store: the app's LocalDataStorage on the phone, a map in unit tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)

    /** Every key in the store; per-watch values move with it when a watch is re-paired ([org.avmedia.gshockGoogleSync.watch.WatchStore.move]). */
    fun keys(): Set<String> = emptySet()
}
