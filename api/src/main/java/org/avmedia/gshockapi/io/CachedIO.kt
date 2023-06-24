package org.avmedia.gshockapi.io

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.WatchDataEvents
import org.json.JSONObject
import java.util.*
import kotlin.reflect.KSuspendFunction1

object CachedIO {

    val cache = WatchValuesCache()
    val resultQueue = ResultQueue<CompletableDeferred<Any>>()

    fun init() {
        cache.clear()
        resultQueue.clear()
    }

    fun clearCache() {
        cache.clear()
    }

    suspend fun request(key: String, func: KSuspendFunction1<String, Any>): Any {
        val value = cache.getCached(key)
        if (value == null) {
            val funcResult = func(key)
            cache.put(key, funcResult)
            return funcResult
        }
        return value
    }

    fun subscribe(subject: String, onDataReceived: (JSONObject) -> Unit): Unit {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.canonicalName, subject) {
            onDataReceived(it as JSONObject)
        }
    }

    fun get(key: String): Any? {
        return cache.get(key)
    }

    fun put(key: String, value: Any): Any? {
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
        val key = shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
        return key
    }
}