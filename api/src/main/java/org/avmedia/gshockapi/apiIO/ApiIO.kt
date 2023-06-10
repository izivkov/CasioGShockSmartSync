package org.avmedia.gshockapi.apiIO

import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.ResultQueue
import org.avmedia.gshockapi.utils.WatchDataEvents
import org.json.JSONObject
import kotlin.reflect.KSuspendFunction1

open class ApiIO {

    suspend fun request(key:String, func: KSuspendFunction1<String, Any>): Any {
        val value = cache.getCached(key)

        if (value == null) {
            val funcResult = func(key)
            cache.put(key, funcResult)
            return funcResult
        }
        return value
    }

    fun send() {
        TODO("Not yet implemented")
    }

    protected fun subscribe(subject: String, onDataReceived: (JSONObject) -> Unit): Unit {
        WatchDataEvents.addSubject(subject)

        // receive values from the commands we issued in start()
        WatchDataEvents.subscribe(this.javaClass.canonicalName, subject) {
            onDataReceived(it as JSONObject)
        }
    }

    protected fun get(key:String) : Any? {
        return cache.get (key)
    }

    protected fun put(key:String, value: Any) : Any? {
        return cache.put (key, value)
    }

    companion object {
        private val cache = WatchValuesCache()
        @JvmStatic
        protected val resultQueue = ResultQueue<CompletableDeferred<Any>>()
    }
}