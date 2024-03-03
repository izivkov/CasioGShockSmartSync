package org.avmedia.gshockapi.io

class WatchValuesCache {
    private val map = mutableMapOf<String, Any>()

    suspend fun getCached(key: String): Any? {
        return get(key.uppercase())
    }

    fun put(key: String, value: Any) {
        map[key.uppercase()] = value
    }

    fun get(key: String): Any? {
        return map[key.uppercase()]
    }

    fun remove(key: String) {
        map.remove(key.uppercase())
    }

    fun clear() {
        map.clear()
    }
}