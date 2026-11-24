package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.smartsync.plugin.api.CommandResult
import org.avmedia.smartsync.plugin.api.Delivery
import org.avmedia.smartsync.plugin.api.WatchId

/**
 * Every write a plugin asks for ([org.avmedia.smartsync.plugin.api.PluginHost.notify], `command`), one at a time:
 * round robin over the plugins (each plugin's own order kept), never while a health or workout transfer holds the link
 * ([transferRunning]; after [busyLimitMs] the result is Busy), each with a time limit. The writes themselves go
 * through the library's watch queue. A write for a watch that is not connected waits for its next connection when it
 * asked for [Delivery.NextConnection], in memory, until it expires. Android-free.
 */
class CommandScheduler(
    scope: CoroutineScope,
    private val connected: (WatchId) -> Boolean,
    private val transferRunning: () -> Boolean,
    private val log: (pluginId: String, line: String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
    private val commandTimeoutMs: Long = 20_000,
    private val busyLimitMs: Long = 120_000,
    private val pollMs: Long = 200,
    private val maxParkedPerPlugin: Int = 50,
) {
    private class Item(
        val pluginId: String,
        val watch: WatchId,
        val delivery: Delivery,
        val what: String,
        val work: suspend () -> CommandResult,
    ) {
        val result = CompletableDeferred<CommandResult>()
        var expiresAt: Long = Long.MAX_VALUE
        /** Taken out of the parking: its caller already got Queued, a later result is only logged. */
        var replayed = false
    }

    private val lock = Any()
    private val queues = LinkedHashMap<String, ArrayDeque<Item>>()
    private val parked = ArrayList<Item>()
    private var turn = 0
    private val signal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch { while (true) { signal.receive(); drain() } }
    }

    /** Queues [work] for [watch]; the result comes when it ran (or was parked, or refused). [what] names it in log lines. */
    suspend fun submit(pluginId: String, watch: WatchId, delivery: Delivery, what: String, work: suspend () -> CommandResult): CommandResult {
        val item = Item(pluginId, watch, delivery, what, work)
        enqueue(item)
        return item.result.await()
    }

    private fun enqueue(item: Item) {
        synchronized(lock) { queues.getOrPut(item.pluginId) { ArrayDeque() }.addLast(item) }
        signal.trySend(Unit)
    }

    /** [watch] connected: its parked writes that did not expire go back in the queue, in their order. */
    fun onConnected(watch: WatchId) {
        val now = clock()
        val back = synchronized(lock) {
            val mine = parked.filter { it.watch == watch }
            parked.removeAll(mine.toSet())
            mine.filter { it.expiresAt > now }.also { live -> (mine - live.toSet()).forEach { log(it.pluginId, "${it.what} for $watch expired") } }
        }
        back.forEach { it.replayed = true; enqueue(it) }
    }

    /** The plugin stopped: everything it queued or parked is dropped. */
    fun drop(pluginId: String) {
        val gone = synchronized(lock) {
            val q = queues.remove(pluginId).orEmpty().toList()
            val p = parked.filter { it.pluginId == pluginId }
            parked.removeAll(p.toSet())
            q + p
        }
        gone.forEach { it.result.complete(CommandResult.Failed("the plugin stopped")) }
    }

    /** The watch's data is gone: its parked writes too. */
    fun forget(watch: WatchId) = synchronized(lock) { parked.removeAll { it.watch == watch } }

    fun parkedCount(pluginId: String? = null): Int = synchronized(lock) { parked.count { pluginId == null || it.pluginId == pluginId } }

    private fun next(): Item? = synchronized(lock) {
        val ids = queues.keys.toList()
        if (ids.isEmpty()) return null
        for (i in ids.indices) {
            val id = ids[(turn + i) % ids.size]
            val q = queues[id] ?: continue
            val item = q.removeFirstOrNull() ?: continue
            if (q.isEmpty()) queues.remove(id)
            // The next turn starts after this plugin: two plugins alternate.
            turn = (ids.indexOf(id) + 1)
            return item
        }
        null
    }

    private suspend fun drain() {
        while (true) {
            val item = next() ?: return
            val result = run(item)
            if (item.replayed && result !is CommandResult.Done && result !is CommandResult.Queued) {
                log(item.pluginId, "${item.what} at the next connection: ${describe(result)}")
            }
            item.result.complete(result)
        }
    }

    private suspend fun run(item: Item): CommandResult {
        if (!connected(item.watch)) return park(item)
        if (transferRunning()) {
            val free = withTimeoutOrNull(busyLimitMs) { while (transferRunning()) delay(pollMs); true }
            if (free == null) return CommandResult.Busy
            if (!connected(item.watch)) return park(item)
        }
        return try {
            withTimeoutOrNull(commandTimeoutMs) { item.work() } ?: CommandResult.Failed("no answer within ${commandTimeoutMs / 1000} s")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            CommandResult.Failed(e.javaClass.simpleName)
        }
    }

    private fun park(item: Item): CommandResult {
        val d = item.delivery as? Delivery.NextConnection ?: return CommandResult.NotConnected
        synchronized(lock) {
            if (d.key != null) parked.removeAll { it.pluginId == item.pluginId && it.watch == item.watch && (it.delivery as? Delivery.NextConnection)?.key == d.key }
            val mine = parked.filter { it.pluginId == item.pluginId }
            if (mine.size >= maxParkedPerPlugin) {
                parked.remove(mine.first())
                log(item.pluginId, "too many writes waiting for a connection: the oldest is dropped")
            }
            item.expiresAt = clock() + d.keepMillis
            parked += item
        }
        return CommandResult.Queued
    }

    companion object {
        fun describe(r: CommandResult): String = when (r) {
            CommandResult.Done -> "done"
            CommandResult.Queued -> "waits for the next connection"
            CommandResult.NotConnected -> "the watch is not connected"
            CommandResult.Busy -> "the watch was busy"
            is CommandResult.NotSupported -> "not supported (${r.reason})"
            is CommandResult.Refused -> "refused (${r.reason})"
            is CommandResult.Failed -> "failed (${r.reason})"
        }
    }
}
