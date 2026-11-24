package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.channels.Channel
import org.avmedia.smartsync.plugin.api.WatchEvent

/**
 * One plugin's events, in order, bounded. When full, the oldest event that a newer one makes stale (values, a
 * setting) is dropped, else the oldest of all, and [WatchEvent.EventsLost] takes the place of the first dropped one
 * (it does not count against [capacity] and is never dropped itself), so the plugin knows to read the state again.
 * One log line per overflow until the queue has drained. Android-free.
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
            if (items.count { it != WatchEvent.EventsLost } >= capacity) {
                val stale = items.indexOfFirst { !isCritical(it) }
                val at = if (stale >= 0) stale else items.indexOfFirst { it != WatchEvent.EventsLost }
                val dropped = items[at]
                items.removeAt(at)
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

    /** A copy of what is queued, oldest first (tests). */
    fun snapshot(): List<WatchEvent> = synchronized(items) { items.toList() }

    /** Drops everything queued; [take] returns null from now on. */
    fun close() {
        synchronized(items) {
            closed = true
            items.clear()
        }
        signal.close()
    }

    companion object {
        /** Events that a later one never replaces: they are dropped last. [WatchEvent.EventsLost] is never dropped. */
        fun isCritical(e: WatchEvent): Boolean = when (e) {
            is WatchEvent.ValuesUpdated, is WatchEvent.SettingChanged, WatchEvent.Wake -> false
            else -> true
        }
    }
}
