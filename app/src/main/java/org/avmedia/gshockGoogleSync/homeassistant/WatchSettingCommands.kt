package org.avmedia.gshockGoogleSync.homeassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.io.SettingWriteResult
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
    /** The connected watch's key: queued values are written to the watch they were asked for, never to another. */
    private val watch: () -> String = { "" },
    /** The connected watch's name: a choice that depends on the model (the watch face) is checked against it. */
    private val watchName: () -> String = { "" },
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

    fun submit(data: Map<String, Any?>) {
        val asked = requested(data)
        if (asked.isEmpty()) {
            log("Home Assistant watch setting refused: no setting named")
            return
        }
        var queued = false
        for ((key, raw) in asked) {
            val setting = WatchSettingRegistry.find(key)
            if (setting == null) {
                log("Home Assistant watch setting refused: unknown setting $key")
                continue
            }
            when (val c = WatchSettingRegistry.check(setting, raw)) {
                is SettingCheck.Refused -> log("Home Assistant watch setting refused: $key, ${c.reason}")
                is SettingCheck.Ok -> synchronized(pending) {
                    val now = watch()
                    if (now != queuedFor) { pending.clear(); queuedFor = now }
                    pending.remove(key) // the newer value goes last
                    pending[key] = c.value
                    queued = true
                }
            }
        }
        if (queued) onQueued(this)
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

    /** Writes the queued values while the watch is ready. One write at a time. */
    suspend fun flush() = mutex.withLock {
        var wrote = false
        synchronized(pending) {
            if (pending.isNotEmpty() && ready() && watch() != queuedFor) {
                log("Home Assistant watch settings dropped (${pending.keys.joinToString()}): asked for another watch")
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
                    log("Home Assistant set $key to $value")
                    wrote = true
                }
                is SettingWriteResult.Failed -> log("Home Assistant set $key to $value failed: ${result.message}")
            }
        }
        if (wrote) afterWrites()
    }

    fun flushLater() {
        scope.launch { flush() }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Set by [HaSync]; the push router, the setup-done hook and the session reach it here. */
        @Volatile var shared: WatchSettingCommands? = null
    }
}
