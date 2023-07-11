package org.avmedia.gshockapi.io

import kotlin.reflect.KSuspendFunction1

class WatchValuesCache {
    private val map = mutableMapOf<String, Any>()

    suspend fun getCached(key: String): Any? {
        return get(key.uppercase())
    }

    suspend fun getCached(key: String, func: KSuspendFunction1<String, Any>): Any {
        val cachedResult = get(key)
        if (cachedResult == null) {
            val funcResult = func(key)
            put(key.uppercase(), funcResult)
            return funcResult
        }

        return cachedResult
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