package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** Where a plugin stands. */
sealed interface PluginState {
    data object Off : PluginState
    data object Active : PluginState
    /** Built against an interface version this app cannot run. */
    data class Incompatible(val pluginVersion: String) : PluginState
    /** Turned off by the host after failures; the person turns it on again in Settings. */
    data class Failed(val reason: String) : PluginState
}

/** The host's limits for plugins. */
data class PluginLimits(
    val queueSize: Int = 64,
    val eventTimeoutMs: Long = 30_000,
    val activateTimeoutMs: Long = 15_000,
    val deactivateTimeoutMs: Long = 5_000,
    /** Failures in a row (exceptions, time limits) before the plugin is turned off. */
    val maxFailures: Int = 5,
)

/**
 * Runs the plugins: each active plugin has its own event queue and its own coroutine, so a slow or blocked plugin never
 * delays another or the watch. An exception (or a time limit) is caught, logged once and counted; [PluginLimits.maxFailures]
 * in a row turn the plugin off ([PluginState.Failed]). Activate and deactivate run in [scope] (off the main thread)
 * with a time limit; deactivating drops the plugin's queued events and commands ([dropCommands]). Android-free.
 */
class PluginRuntime(
    private val scope: CoroutineScope,
    private val hostFor: (WatchPlugin) -> PluginHost,
    private val log: (pluginName: String, line: String) -> Unit,
    private val limits: PluginLimits = PluginLimits(),
    private val hostApiVersion: String = PluginApi.VERSION,
    /** A plugin was turned off after failures: kept, so it stays off after a restart until the person turns it on. */
    private val onFailed: (id: String, reason: String) -> Unit = { _, _ -> },
    private val dropCommands: (id: String) -> Unit = {},
) {
    private class Slot(val plugin: WatchPlugin) {
        var state: PluginState = PluginState.Off
        var queue: PluginEventQueue? = null
        var job: Job? = null
        var failures = 0
        var loggedFailure = false
        @Volatile var needs = PluginNeeds()
    }

    private val slots = LinkedHashMap<String, Slot>()
    private val lifecycle = Mutex()
    private val _states = MutableStateFlow<Map<String, PluginState>>(emptyMap())
    val states: StateFlow<Map<String, PluginState>> = _states

    /** Registers [plugins] (a second plugin with the same id is skipped with a log line). */
    fun register(plugins: List<WatchPlugin>) = synchronized(slots) {
        for (p in plugins) {
            val id = runCatching { p.id }.getOrNull()
            if (id == null || !ID.matches(id)) { log(p.javaClass.simpleName, "plugin skipped: bad id"); continue }
            if (id in slots) { log(p.javaClass.simpleName, "plugin skipped: id $id is taken"); continue }
            slots[id] = Slot(p)
        }
        publish()
    }

    fun plugins(): List<WatchPlugin> = synchronized(slots) { slots.values.map { it.plugin } }

    fun state(id: String): PluginState = synchronized(slots) { slots[id]?.state } ?: PluginState.Off

    /** Starts [id] when it is off, failed or never started. False when it cannot run (incompatible, unknown, failed to start). */
    suspend fun activate(id: String): Boolean = lifecycle.withLock {
        val slot = synchronized(slots) { slots[id] } ?: return false
        if (slot.state == PluginState.Active) return true
        val name = nameOf(slot)
        val version = runCatching { slot.plugin.apiVersion }.getOrDefault("")
        if (!PluginApi.compatible(version, hostApiVersion)) {
            setState(slot, PluginState.Incompatible(version))
            log(name, "not started: built for plugin interface $version, this app has $hostApiVersion")
            return false
        }
        slot.failures = 0
        slot.loggedFailure = false
        slot.needs = PluginNeeds()
        val queue = PluginEventQueue(limits.queueSize) { log(name, it) }
        slot.queue = queue
        val started = guarded(slot, "start") { withTimeout(limits.activateTimeoutMs) { slot.plugin.activate(hostFor(slot.plugin)) } }
        if (!started) {
            queue.close()
            slot.queue = null
            setState(slot, PluginState.Failed("could not start"))
            onFailed(id, "could not start")
            runCatching { withTimeout(limits.deactivateTimeoutMs) { slot.plugin.deactivate() } }
            return false
        }
        setState(slot, PluginState.Active)
        slot.job = scope.launch { consume(slot, queue) }
        true
    }

    /** Stops [id]: its queue and its commands are dropped. */
    suspend fun deactivate(id: String) = lifecycle.withLock {
        val slot = synchronized(slots) { slots[id] } ?: return@withLock
        stop(slot, PluginState.Off)
    }

    private suspend fun stop(slot: Slot, next: PluginState) {
        val wasActive = slot.state == PluginState.Active
        slot.queue?.close()
        slot.queue = null
        slot.job?.cancel()
        slot.job = null
        slot.needs = PluginNeeds()
        setState(slot, next)
        runCatching { dropCommands(slot.plugin.id) }
        if (wasActive) guarded(slot, "stop", count = false) { withTimeout(limits.deactivateTimeoutMs) { slot.plugin.deactivate() } }
    }

    /** Puts [event] in the queue of every active plugin ([WatchEvent.ButtonPressed] only for those that asked). */
    fun dispatch(event: WatchEvent) {
        val targets = synchronized(slots) { slots.values.filter { it.state == PluginState.Active } }
        for (slot in targets) {
            if (event is WatchEvent.ButtonPressed && !slot.needs.buttonPress) continue
            slot.queue?.offer(event)
        }
    }

    fun declareNeeds(id: String, needs: PluginNeeds) {
        val slot = synchronized(slots) { slots[id] } ?: return
        slot.needs = needs
    }

    /** What the active plugins need together. */
    fun needs(): PluginNeeds {
        val active = synchronized(slots) { slots.values.filter { it.state == PluginState.Active }.map { it.needs } }
        val values: Set<String>? = if (active.any { it.values == null }) null else active.flatMap { it.values.orEmpty() }.toSet()
        return PluginNeeds(
            values = values,
            buttonPress = active.any { it.buttonPress },
            healthReads = active.any { it.healthReads },
            keepConnected = active.any { it.keepConnected },
            undecodedSettings = active.any { it.undecodedSettings },
        )
    }

    /** The needs of [id] while it is active. */
    fun needsOf(id: String): PluginNeeds? = synchronized(slots) { slots[id]?.takeIf { it.state == PluginState.Active }?.needs }

    private suspend fun consume(slot: Slot, queue: PluginEventQueue) {
        while (true) {
            val event = queue.take() ?: return
            val ok = guarded(slot, event::class.simpleName ?: "event") {
                withTimeout(limits.eventTimeoutMs) { slot.plugin.onEvent(event) }
            }
            if (!ok && slot.failures >= limits.maxFailures) {
                val reason = "turned off after ${slot.failures} failures in a row"
                log(nameOf(slot), reason)
                onFailed(slot.plugin.id, reason)
                // Not in the lifecycle lock: this coroutine is the one stop() cancels.
                scope.launch { lifecycle.withLock { if (slot.state == PluginState.Active) stop(slot, PluginState.Failed(reason)) } }
                return
            }
        }
    }

    /** Runs [block]; a throw or a time limit is logged once per run of failures and counted. True when it worked. */
    private suspend fun guarded(slot: Slot, what: String, count: Boolean = true, block: suspend () -> Unit): Boolean =
        try {
            block()
            slot.failures = 0
            slot.loggedFailure = false
            true
        } catch (e: Throwable) {
            val timedOut = e is kotlinx.coroutines.TimeoutCancellationException
            if (e is CancellationException && !timedOut) throw e
            if (count) slot.failures++
            if (!slot.loggedFailure) {
                slot.loggedFailure = true
                val why = if (timedOut) "took too long" else "${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}"
                log(nameOf(slot), "$what failed ($why)")
            }
            false
        }

    private fun nameOf(slot: Slot): String = runCatching { slot.plugin.name }.getOrDefault(slot.plugin.javaClass.simpleName)

    private fun setState(slot: Slot, state: PluginState) {
        synchronized(slots) { slot.state = state }
        publish()
    }

    private fun publish() {
        _states.value = synchronized(slots) { slots.mapValues { it.value.state } }
    }

    private companion object {
        val ID = Regex("[a-z0-9_]{1,40}")
    }
}
