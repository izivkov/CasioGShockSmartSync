package org.avmedia.gshockapi.utils

import timber.log.Timber

class ResultQueue<T> {
    private var queue: MutableList<T> = mutableListOf()

    fun enqueue(element: T) {
        queue.add(element)
    }

    fun dequeue(): T? {

        return if (queue.isEmpty()) {
            Timber.d("*** dequeue: Nothing to dequeue ***")
            null
        } else {
            queue.removeAt(0)
        }
    }

    fun peek(): T? {
        return queue.firstOrNull()
    }

    fun isEmpty(): Boolean {
        return queue.isEmpty()
    }

    fun size(): Int {
        return queue.size
    }

    fun clear() {
        queue.clear()
    }
}