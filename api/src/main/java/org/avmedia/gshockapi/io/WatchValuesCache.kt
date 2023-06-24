package org.avmedia.gshockapi.io

import kotlin.reflect.KSuspendFunction1

class WatchValuesCache {
    private val map = mutableMapOf<String, Any>()

    suspend fun getCached(key: String): Any? {
        return get(key)
    }

    suspend fun getCached(key: String, func: KSuspendFunction1<String, Any>): Any {
        val cachedResult = get(key)
        if (cachedResult == null) {
            val funcResult = func(key)
            put(key, funcResult)
            return funcResult
        }

        return cachedResult
    }

    fun put(key: String, value: Any) {
        map[key] = value
    }

    fun get(key: String): Any? {
        return map[key]
    }

    fun remove(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }
}