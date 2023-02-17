package org.avmedia.gshockapi.utils

class ResultQueue<T> {
    private var queue: MutableList<T> = mutableListOf()

    fun enqueue(element: T) {
        if (size() > 0) {
            clear()
        }
        queue.add(element)
    }

    fun dequeue(): T? {
        return if (queue.isEmpty()) {
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