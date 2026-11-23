package org.avmedia.gshockGoogleSync.homeassistant

/** A string store: the app's LocalDataStorage on the phone, a map in unit tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)
}
