package org.avmedia.gshockapi.utils

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ResultQueue<T> {

    data class KeyedResult(val key: String, val result: CompletableDeferred<Any>) {
        override fun toString(): String {
            return "KeyedResult(key='$key', result=$result)"
        }
    }

    private val keyedResultMap: ConcurrentMap<String, CompletableDeferred<Any>> =
        ConcurrentHashMap()

    fun enqueue(element: KeyedResult) {
        keyedResultMap[element.key.uppercase()] = element.result
    }

    fun dequeue(_key: String): CompletableDeferred<Any>? {

        return if (keyedResultMap.isEmpty()) {
            null
        } else {
            val key = _key.uppercase()
            val value = keyedResultMap[key]
            keyedResultMap.remove(key)
            value
        }
    }

    fun isEmpty(): Boolean {
        return keyedResultMap.isEmpty()
    }

    fun size(): Int {
        return keyedResultMap.size
    }

    fun clear() {
        keyedResultMap.clear()
    }
}