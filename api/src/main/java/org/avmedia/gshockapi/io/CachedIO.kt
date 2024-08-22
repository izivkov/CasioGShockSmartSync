package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.utils.Utils
import java.util.Locale
import kotlin.reflect.KFunction0
import kotlin.reflect.KSuspendFunction1

object CachedIO {

    private var cacheOff = false
    private val cache = Cache()

    class Cache {
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

    fun init() {
        cache.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    suspend fun request(key: String, func: KSuspendFunction1<String, Any>): Any {
        val value = if (cacheOff) null else cache.getCached(key)
        if (value == null) {
            val funcResult = func(key)
            cache.put(key, funcResult)
            return funcResult
        }
        return value
    }

    fun set(key: String, func: KFunction0<Unit>) {
        func()
        remove(key)
    }

    fun get(key: String): Any? {
        return cache.get(key)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun put(key: String, value: Any): Any {
        return cache.put(key, value)
    }

    fun createKey(data: String): String {

        val shortStr = Utils.toCompactString(data)
        var keyLength = 2
        // get the first byte of the returned data, which indicates the data content.
        val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
        if (startOfData in arrayOf("1D", "1E", "1F", "30", "31")) {
            keyLength = 4
        }
        return shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
    }
}