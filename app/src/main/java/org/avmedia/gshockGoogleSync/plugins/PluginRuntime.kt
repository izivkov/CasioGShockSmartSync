package org.avmedia.gshockGoogleSync.plugins

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.smartsync.plugin.api.Capability
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
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

/** The host's limits for plugins (the minimum the interface promises: WatchPlugin KDoc). */
data class PluginLimits(
    val queueSize: Int = 64,
    val eventTimeoutMs: Long = 30_000,
    val activateTimeoutMs: Long = 15_000,
    val deactivateTimeoutMs: Long = 5_000,
    /** Failures in a row (exceptions, time limits) before the plugin is turned off. */
    val maxFailures: Int = 5,
    /** After a time limit: how long a cancelled callback may take to end before the plugin counts as blocked. */
    val graceMs: Long = 1_000,
    /** At stop: how long a running onEvent may take to end before deactivate (skipped when it does not end). */
    val stopWaitMs: Long = 5_000,
)

/** A host object the runtime revokes when its plugin stops ([org.avmedia.smartsync.plugin.api.FailReason.STOPPED]). */
interface RevocableHost {
    fun revoke()
}

/**
 * Runs the plugins: each active plugin has its own event queue and its own coroutine, so a slow or blocked plugin never
 * delays another or the watch. Every callback runs in a job of its own with a time limit that time inside host calls
 * does not use up ([enterHost]); a callback that ignores its cancellation (blocked in a socket, `Thread.sleep`) turns
 * the plugin off at once. An exception (or a time limit) is caught, logged once and counted;
 * [PluginLimits.maxFailures] in a row turn the plugin off ([PluginState.Failed]). Each plugin has its own lifecycle
 * lock. Stopping revokes the plugin's host and drops its queued events and commands ([dropCommands]). Android-free.
 */
class PluginRuntime(
    private val scope: CoroutineScope,
    private val hostFor: (WatchPlugin) -> PluginHost,
    private val log: (pluginId: String, line: String) -> Unit,
    private val limits: PluginLimits = PluginLimits(),
    private val hostApiVersion: String = PluginApi.VERSION,
    /** A plugin was turned off after failures: kept, so it stays off after a restart until the person turns it on. */
    private val onFailed: (id: String, reason: String) -> Unit = { _, _ -> },
    private val dropCommands: (id: String) -> Unit = {},
    /** A callback threw or took too long ([text]: what and why), and the next one that worked. */
    private val onCaught: (id: String, text: String) -> Unit = { _, _ -> },
    private val onRecovered: (id: String) -> Unit = {},
    /** What a watch can do (its capabilities at its last connection), to keep each plugin to the watches it works with. */
    private val capsOf: (WatchId) -> Set<Capability> = { emptySet() },
    /** The kind of data of each value key ([AccessGuard]); null: never served. */
    private val uses: ValueUses = { null },
    /** Every value key the host can read, for a plugin that asks for all of its kinds ([PluginNeeds.values] null). */
    private val allKeys: () -> Collection<String> = { emptyList() },
    /** The value keys of the decoded watch settings, for [PluginNeeds.settings]. */
    private val settingKeys: () -> Collection<String> = { emptyList() },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Slot(val id: String, val plugin: WatchPlugin) {
        val access: PluginAccess = runCatching { plugin.access }.getOrDefault(PluginAccess())
        val apiVersion: String = runCatching { plugin.apiVersion }.getOrDefault("")
        val lifecycle = Mutex()
        @Volatile var state: PluginState = PluginState.Off
        /** Events are queued from the start of activate on. */
        @Volatile var accepting = false
        @Volatile var queue: PluginEventQueue? = null
        @Volatile var job: Job? = null
        @Volatile var current: Job? = null
        @Volatile var host: PluginHost? = null
        var failures = 0
        var loggedFailure = false
        @Volatile var needs = PluginNeeds()
        /** Time spent in host calls: ended ones in [hostMillis], a running one since [hostSince] while [hostCalls] > 0. */
        val hostMillis = AtomicLong()
        var hostCalls = 0
        var hostSince = 0L
        @Volatile var lastWake = Long.MIN_VALUE
        /** Waiting for this plugin's next [WatchEvent.Wake] to be handled ([wakeAndWait]). */
        val wakeWaiters = java.util.concurrent.ConcurrentLinkedQueue<kotlinx.coroutines.CompletableDeferred<Unit>>()
        val connectedSeen: MutableSet<WatchId> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }

    private enum class Outcome { OK, FAILED, BLOCKED }

    private val slots = LinkedHashMap<String, Slot>()
    private val _states = MutableStateFlow<Map<String, PluginState>>(emptyMap())
    val states: StateFlow<Map<String, PluginState>> = _states

    /** Registers [plugins] (a bad, reserved or second id is skipped with a log line). The id is read once, here. */
    fun register(plugins: List<WatchPlugin>) = synchronized(slots) {
        for (p in plugins) {
            val id = runCatching { p.id }.getOrNull()
            if (id == null || !ID.matches(id)) { log(p.javaClass.simpleName, "plugin skipped: bad id"); continue }
            if (id in RESERVED) { log(p.javaClass.simpleName, "plugin skipped: id $id is reserved by the app"); continue }
            if (id in slots) { log(p.javaClass.simpleName, "plugin skipped: id $id is taken"); continue }
            slots[id] = Slot(id, p)
        }
        publish()
    }

    fun plugins(): List<WatchPlugin> = synchronized(slots) { slots.values.map { it.plugin } }

    /** The registered ids, in the order of [plugins]. */
    fun ids(): List<String> = synchronized(slots) { slots.keys.toList() }

    /** The plugin registered under [id]. */
    fun plugin(id: String): WatchPlugin? = synchronized(slots) { slots[id]?.plugin }

    fun state(id: String): PluginState = synchronized(slots) { slots[id]?.state } ?: PluginState.Off

    private fun slot(id: String): Slot? = synchronized(slots) { slots[id] }

    /** Starts [id] when it is off, failed or never started. False when it cannot run (incompatible, unknown, failed to start). */
    suspend fun activate(id: String): Boolean {
        val slot = slot(id) ?: return false
        return slot.lifecycle.withLock {
            if (slot.state == PluginState.Active) return@withLock true
            if (!PluginApi.compatible(slot.apiVersion, hostApiVersion)) {
                setState(slot, PluginState.Incompatible(slot.apiVersion))
                log(id, "not started: built for plugin interface ${slot.apiVersion}, this app has $hostApiVersion")
                return@withLock false
            }
            slot.failures = 0
            slot.loggedFailure = false
            slot.needs = PluginNeeds()
            slot.connectedSeen.clear()
            slot.lastWake = Long.MIN_VALUE
            val queue = PluginEventQueue(limits.queueSize) { log(id, it) }
            slot.queue = queue
            slot.accepting = true
            val host = hostFor(slot.plugin)
            slot.host = host
            val started = call(slot, "start", limits.activateTimeoutMs) { slot.plugin.activate(host) }
            if (started != Outcome.OK) {
                slot.accepting = false
                queue.close()
                slot.queue = null
                revoke(slot)
                val reason = if (started == Outcome.BLOCKED) "blocked while starting" else "could not start"
                setState(slot, PluginState.Failed(reason))
                onFailed(id, reason)
                runCatching { dropCommands(id) }
                if (started != Outcome.BLOCKED) call(slot, "stop", limits.deactivateTimeoutMs, count = false) { slot.plugin.deactivate() }
                return@withLock false
            }
            setState(slot, PluginState.Active)
            slot.job = scope.launch { consume(slot, queue) }
            true
        }
    }

    /** Stops [id]: its host is revoked, its queue and its commands are dropped. */
    suspend fun deactivate(id: String) {
        val slot = slot(id) ?: return
        slot.lifecycle.withLock { stop(slot, PluginState.Off) }
    }

    private suspend fun stop(slot: Slot, next: PluginState) {
        val wasActive = slot.state == PluginState.Active
        slot.accepting = false
        slot.queue?.close()
        slot.queue = null
        val consumer = slot.job
        val running = slot.current
        slot.job = null
        consumer?.cancel()
        running?.cancel()
        // deactivate never runs while onEvent does: wait for it to end first.
        val ended = withTimeoutOrNull(limits.stopWaitMs) { consumer?.join(); running?.join(); true } == true
        slot.needs = PluginNeeds()
        releaseWakeWaiters(slot)
        setState(slot, next)
        revoke(slot)
        runCatching { dropCommands(slot.id) }
        if (!wasActive) return
        if (ended) call(slot, "stop", limits.deactivateTimeoutMs, count = false) { slot.plugin.deactivate() }
        else log(slot.id, "stop: a callback did not end in ${limits.stopWaitMs / 1000} s, not deactivated")
    }

    private fun revoke(slot: Slot) {
        val h = slot.host ?: return
        slot.host = null
        runCatching { (h as? RevocableHost)?.revoke() }
    }

    /** Puts [event] in the queue of every active (or starting) plugin ([WatchEvent.ButtonPressed] only for those that asked). */
    fun dispatch(event: WatchEvent) {
        val targets = synchronized(slots) { slots.values.filter { it.accepting } }
        for (slot in targets) deliver(slot, event)
    }

    /**
     * Puts [event] in the queue of [id] only: what a plugin that just started missed. A [WatchEvent.Connected] it got
     * already while starting is not sent twice.
     */
    fun dispatchTo(id: String, event: WatchEvent) {
        val slot = slot(id)?.takeIf { it.state == PluginState.Active } ?: return
        if (event is WatchEvent.Connected && event.watch in slot.connectedSeen) return
        deliver(slot, event)
    }

    private fun deliver(slot: Slot, event: WatchEvent): Boolean {
        if (event is WatchEvent.ButtonPressed && !slot.needs.buttonPress) return false
        // Never an event type newer than the interface the plugin was built against.
        if (!PluginApi.atLeast(slot.apiVersion, AccessGuard.since(event))) return false
        // Only what the plugin declared ([PluginAccess]).
        val seen = AccessGuard.event(slot.access, uses, event) ?: return false
        if (!servesWatch(slot, event)) return false
        if (event is WatchEvent.Connected) slot.connectedSeen += event.watch
        if (event is WatchEvent.Disconnected) slot.connectedSeen -= event.watch
        return slot.queue?.offer(seen) == true
    }

    /** A watch the plugin cannot work with (a required capability missing) gets nothing. */
    private fun servesWatch(slot: Slot, event: WatchEvent): Boolean {
        val w = event.watch ?: return true
        return WatchSupport.of(slot.access, runCatching { capsOf(w) }.getOrDefault(emptySet())) !is WatchSupport.NotSupported
    }

    fun declareNeeds(id: String, needs: PluginNeeds) {
        val slot = slot(id) ?: return
        slot.needs = needs
    }

    /** [id] entered a host call: until [exitHost], the time does not count against its callback's limit. */
    fun enterHost(id: String) {
        val s = slot(id) ?: return
        synchronized(s) { if (s.hostCalls++ == 0) s.hostSince = clock() }
    }

    fun exitHost(id: String) {
        val s = slot(id) ?: return
        synchronized(s) { if (s.hostCalls > 0 && --s.hostCalls == 0) s.hostMillis.addAndGet(clock() - s.hostSince) }
    }

    /** The time [s] spent in host calls so far, a running call included. */
    private fun hostTime(s: Slot): Long = synchronized(s) { s.hostMillis.get() + if (s.hostCalls > 0) clock() - s.hostSince else 0 }

    /**
     * What the active plugins need together. [PluginNeeds.values] is never null here: "every key" of a plugin is every
     * key of a kind it declared, and [PluginNeeds.settings] adds the setting keys its kinds cover.
     */
    fun needs(): PluginNeeds {
        val active = synchronized(slots) { slots.values.filter { it.state == PluginState.Active }.map { covered(it) } }
        return PluginNeeds(
            values = active.flatMap { it.values.orEmpty() }.toSet(),
            settings = active.any { it.settings },
            buttonPress = active.any { it.buttonPress },
            healthReads = active.any { it.healthReads },
            wakeEveryMinutes = active.mapNotNull { it.wakeEveryMinutes }.minOrNull(),
        )
    }

    /** The needs of [id] while it is active. */
    fun needsOf(id: String): PluginNeeds? = synchronized(slots) { slots[id]?.takeIf { it.state == PluginState.Active }?.let { covered(it) } }

    /** [slot]'s needs as far as its declaration covers them. */
    private fun covered(s: Slot): PluginNeeds = s.needs.copy(
        buttonPress = s.needs.buttonPress && Control.BUTTON_PRESS in s.access.controls,
        values = ((s.needs.values ?: allKeys().toSet()) + (emptyList<String>()))
            .filter { s.needs.values == null || AccessGuard.mayRead(s.access, uses, it) }.toSet(),
        settings = s.needs.settings && (DataUse.WATCH_SETTINGS in s.access.reads || DataUse.BODY_PROFILE in s.access.reads),
        healthReads = s.needs.healthReads && AccessGuard.readsHealth(s.access),
        wakeEveryMinutes = s.needs.wakeEveryMinutes?.coerceAtLeast(MIN_WAKE_MINUTES),
    )

    /** The periodic job ran at [nowMillis]: [WatchEvent.Wake] for each active plugin whose interval passed. The ids woken. */
    fun wake(nowMillis: Long): List<String> = wake(nowMillis, null)

    private fun wake(nowMillis: Long, waits: MutableList<kotlinx.coroutines.CompletableDeferred<Unit>>?): List<String> {
        val due = synchronized(slots) {
            slots.values.filter { s ->
                val every = covered(s).wakeEveryMinutes ?: return@filter false
                s.state == PluginState.Active && (s.lastWake == Long.MIN_VALUE || nowMillis - s.lastWake >= every * 60_000L - WAKE_SLACK_MS)
            }
        }
        return due.filter { s ->
            // The waiter goes in before the event: the plugin may handle it at once.
            val wait = waits?.let { kotlinx.coroutines.CompletableDeferred<Unit>().also { d -> s.wakeWaiters += d } }
            deliver(s, WatchEvent.Wake).also { ok ->
                if (ok) { s.lastWake = nowMillis; wait?.let { waits?.add(it) } } else wait?.let { s.wakeWaiters.remove(it); it.complete(Unit) }
            }
        }.map { it.id }
    }

    /**
     * [wake], then waits until each woken plugin has handled its [WatchEvent.Wake] (or stopped), [timeoutMs] at most:
     * the periodic job keeps the process and the network that long. The ids woken.
     */
    suspend fun wakeAndWait(nowMillis: Long, timeoutMs: Long = 60_000): List<String> {
        val waits = ArrayList<kotlinx.coroutines.CompletableDeferred<Unit>>()
        val woken = wake(nowMillis, waits)
        return woken
    }

    private fun releaseWakeWaiters(slot: Slot) {
        while (true) (slot.wakeWaiters.poll() ?: return).complete(Unit)
    }

    private suspend fun consume(slot: Slot, queue: PluginEventQueue) {
        while (true) {
            val event = queue.take() ?: return
            val outcome = call(slot, event::class.simpleName ?: "event", limits.eventTimeoutMs) { slot.plugin.onEvent(event) }
            if (event == WatchEvent.Wake) releaseWakeWaiters(slot)
            val reason = when {
                outcome == Outcome.BLOCKED -> "turned off: blocked in ${event::class.simpleName}"
                outcome == Outcome.FAILED && slot.failures >= limits.maxFailures -> "turned off after ${slot.failures} failures in a row"
                else -> null
            } ?: continue
            log(slot.id, reason)
            onFailed(slot.id, reason)
            // Not in the lifecycle lock: this coroutine is the one stop() cancels.
            scope.launch { slot.lifecycle.withLock { if (slot.state == PluginState.Active) stop(slot, PluginState.Failed(reason)) } }
            return
        }
    }

    /**
     * Runs [block] in a job of its own and waits for it up to [limitMs] plus the time spent in host calls meanwhile.
     * A throw (also the plugin's own CancellationException) or a time limit is logged once per run of failures and
     * counted; a callback that does not end within [PluginLimits.graceMs] after its cancellation is [Outcome.BLOCKED].
     * Only the caller's own cancellation is rethrown.
     */
    private suspend fun call(slot: Slot, what: String, limitMs: Long, count: Boolean = true, block: suspend () -> Unit): Outcome {
        val job = scope.async { block() }
        slot.current = job
        val hostAtStart = hostTime(slot)
        val deadline = clock() + limitMs
        var timedOut = false
        try {
            while (true) {
                val wait = deadline + (hostTime(slot) - hostAtStart) - clock()
                if (wait <= 0) { timedOut = true; break }
                if (withTimeoutOrNull(wait) { job.join(); true } == true) break
            }
        } catch (e: CancellationException) {
            // The caller (stop) is cancelling: the callback goes too; stop waits for it.
            job.cancel()
            throw e
        }
        val error: Throwable? = if (timedOut) {
            job.cancel()
            if (withTimeoutOrNull(limits.graceMs) { job.join(); true } != true) {
                fail(slot, what, "blocked", count)
                return Outcome.BLOCKED
            }
            null
        } else try { job.await(); null } catch (e: Throwable) { e }
        slot.current = null
        if (!timedOut && error == null) {
            if (slot.loggedFailure) runCatching { onRecovered(slot.id) }
            slot.failures = 0
            slot.loggedFailure = false
            return Outcome.OK
        }
        // The class only: a message can hold a URL or a value.
        fail(slot, what, if (timedOut) "took too long" else error!!.javaClass.simpleName, count)
        return Outcome.FAILED
    }

    private fun fail(slot: Slot, what: String, why: String, count: Boolean) {
        if (count) slot.failures++
        runCatching { onCaught(slot.id, "$what failed ($why)") }
        if (!slot.loggedFailure) {
            slot.loggedFailure = true
            log(slot.id, "$what failed ($why)")
        }
    }

    private fun setState(slot: Slot, state: PluginState) {
        synchronized(slots) { slot.state = state }
        publish()
    }

    private fun publish() {
        _states.value = synchronized(slots) { slots.mapValues { it.value.state } }
    }

    companion object {
        private val ID = Regex("[a-z0-9_]{1,40}")

        /** Ids the app uses itself: "app" names the host's own writes, "webhooks" a removed feature's switch key. */
        val RESERVED = setOf(CommandScheduler.HOST, "webhooks")

        const val MIN_WAKE_MINUTES = 15
        private const val WAKE_SLACK_MS = 60_000L
    }
}
