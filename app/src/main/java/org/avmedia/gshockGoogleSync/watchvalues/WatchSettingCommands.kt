package org.avmedia.gshockGoogleSync.watchvalues

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.smartsync.plugin.api.Delivered
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult
import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource

/**
 * Watch settings that Home Assistant asked for (`command_watch_setting`). A request is checked against the
 * [WatchSettingRegistry] first: an unknown key or a bad value is one App log line and nothing is queued. The latest
 * value per key waits until the watch is connected and its setup is done (`47 01`), then each is written with the
 * app's checked write. A failed write is logged once and not retried. [write] does the write; [afterWrites] runs once
 * after a batch (it pushes the sensors, so Home Assistant sees the value read back).
 */
class WatchSettingCommands(
    private val write: suspend (WatchSetting, Any) -> SettingWriteResult,
    private val connected: () -> Boolean,
    private val afterWrites: () -> Unit = {},
    private val log: (String) -> Unit = { AppLog.log(LogSource.App, it) },
    private val onQueued: (WatchSettingCommands) -> Unit = { it.flushLater() },
    /** After each good write: [key] now has [value] (a setting changed event). */
    private val onWritten: (key: String, value: Any) -> Unit = { _, _ -> },
    /** The connected watch's key: queued values are written to the watch they were asked for, never to another. */
    private val watch: () -> String = { "" },
    /** The connected watch's name: a choice that depends on the model (the watch face) is checked against it. */
    private val watchName: () -> String = { "" },
    /** Who asked, at the start of each log line (the plugin's name). */
    private val who: String = "Home Assistant",
) {
    private val pending = LinkedHashMap<String, Any>()
    /** The watch the queued values are for. */
    @Volatile private var queuedFor: String = ""
    private val mutex = Mutex()
    @Volatile private var setupDone = false

    /** What a request holds: `{setting, value}` or `{settings: {key: value}}` (both may be present). */
    fun requested(data: Map<String, Any?>): Map<String, Any?> {
        val all = LinkedHashMap<String, Any?>()
        (data["settings"] as? Map<*, *>)?.forEach { (k, v) -> if (k is String) all[k] = v }
        (data["setting"] as? String)?.let { all[it] = data["value"] }
        return all
    }

    /** Checks and queues the asked values; the number queued (refused ones are one log line each). */
    fun submit(data: Map<String, Any?>): Int {
        val asked = requested(data)
        if (asked.isEmpty()) {
            log("$who watch setting refused: no setting named")
            return 0
        }
        var count = 0
        var queued = false
        for ((key, raw) in asked) {
            val setting = WatchSettingRegistry.find(key)
            if (setting == null) {
                log("$who watch setting refused: unknown setting $key")
                continue
            }
            when (val c = WatchSettingRegistry.check(setting, raw, watchName())) {
                is SettingCheck.Refused -> log("$who watch setting refused: $key, ${c.reason}")
                is SettingCheck.Ok -> synchronized(pending) {
                    val now = watch()
                    if (now != queuedFor) { pending.clear(); queuedFor = now }
                    pending.remove(key) // the newer value goes last
                    pending[key] = c.value
                    queued = true
                    count++
                }
            }
        }
        if (queued) onQueued(this)
        return count
    }

    /**
     * For a plugin's command: checks and queues [data], waits up to [readyWaitMs] for the watch's setup, then writes.
     * The caller runs it through the host's write queue, so nothing else writes meanwhile.
     */
    suspend fun apply(data: Map<String, Any?>, readyWaitMs: Long = 15_000): PluginResult<Delivered> {
        if (submit(data) == 0) return PluginResult.Failed(FailReason.INVALID, "no valid setting")
        val ready = kotlinx.coroutines.withTimeoutOrNull(readyWaitMs) { while (!ready()) kotlinx.coroutines.delay(200); true }
        if (ready == null) {
            synchronized(pending) { pending.clear() }
            log("$who watch settings not written: the watch is not ready")
            return PluginResult.Failed(FailReason.NOT_CONNECTED, "the watch is not ready")
        }
        val (_, failed) = flush()
        return if (failed == 0) PluginResult.Ok(Delivered.NOW)
        else PluginResult.Failed(FailReason.INTERNAL, "$failed write(s) failed")
    }

    fun pendingKeys(): List<String> = synchronized(pending) { pending.keys.toList() }

    /** `47 01`: the watch's setup is done. */
    fun onSetupDone() {
        setupDone = true
        if (pendingKeys().isNotEmpty()) onQueued(this)
    }

    /** The link ended: nothing is written until the next setup. */
    fun onDisconnected() {
        setupDone = false
    }

    fun ready(): Boolean = setupDone && connected()

    /** Writes the queued values while the watch is ready. One write at a time. Returns (written, failed). */
    suspend fun flush(): Pair<Int, Int> = mutex.withLock {
        var wrote = false
        var written = 0
        var failed = 0
        synchronized(pending) {
            if (pending.isNotEmpty() && ready() && watch() != queuedFor) {
                log("$who watch settings dropped (${pending.keys.joinToString()}): asked for another watch")
                pending.clear()
            }
        }
        while (ready()) {
            val (key, value) = synchronized(pending) {
                val first = pending.entries.firstOrNull() ?: return@synchronized null
                pending.remove(first.key)
                first.key to first.value
            } ?: break
            val setting = WatchSettingRegistry.find(key) ?: continue
            val result = runCatching { write(setting, value) }.getOrElse { SettingWriteResult.Failed("the write failed") }
            when (result) {
                SettingWriteResult.Done, SettingWriteResult.Written, SettingWriteResult.Unchanged -> {
                    log("$who set $key to $value")
                    wrote = true
                    written++
                    onWritten(key, value)
                }
                is SettingWriteResult.Failed -> {
                    log("$who set $key to $value failed: ${result.message}")
                    failed++
                }
            }
        }
        if (wrote) afterWrites()
        written to failed
    }

    fun flushLater() {
        scope.launch { flush() }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Set by the plugin hub; the setup-done hook and the session reach it here. */
        @Volatile var shared: WatchSettingCommands? = null
    }
}
