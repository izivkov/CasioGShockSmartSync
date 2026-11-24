package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.channels.Channel
import org.avmedia.smartsync.plugin.api.WatchEvent

/**
 * One plugin's events, in order, bounded. When full, the oldest event that a newer one makes stale (values, condition,
 * a setting) is dropped, else the oldest of all; one log line per overflow until the queue has drained. Android-free.
 */
class PluginEventQueue(
    private val capacity: Int,
    private val log: (String) -> Unit,
) {
    private val items = ArrayDeque<WatchEvent>()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var overflowing = false
    @Volatile private var closed = false

    /** False when the queue is closed (the plugin stopped). */
    fun offer(event: WatchEvent): Boolean {
        synchronized(items) {
            if (closed) return false
            if (items.size >= capacity) {
                val stale = items.indexOfFirst { !isCritical(it) }
                val dropped = if (stale >= 0) items.removeAt(stale) else items.removeFirst()
                if (!overflowing) {
                    overflowing = true
                    log("events queue full ($capacity): dropped ${dropped::class.simpleName} and newer stale events")
                }
            }
            items.addLast(event)
        }
        signal.trySend(Unit)
        return true
    }

    /** The next event, waiting for one; null once closed. */
    suspend fun take(): WatchEvent? {
        while (true) {
            synchronized(items) {
                if (closed) return null
                items.removeFirstOrNull()?.let {
                    if (items.isEmpty()) overflowing = false
                    return it
                }
            }
            if (signal.receiveCatching().isClosed) return null
        }
    }

    fun size(): Int = synchronized(items) { items.size }

    /** Drops everything queued; [take] returns null from now on. */
    fun close() {
        synchronized(items) {
            closed = true
            items.clear()
        }
        signal.close()
    }

    companion object {
        /** Events that a later one never replaces: they are dropped last. */
        fun isCritical(e: WatchEvent): Boolean = when (e) {
            is WatchEvent.ValuesUpdated, is WatchEvent.Condition, is WatchEvent.SettingChanged -> false
            else -> true
        }
    }
}
