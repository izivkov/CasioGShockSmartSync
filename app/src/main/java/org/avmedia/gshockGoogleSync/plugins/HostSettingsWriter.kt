package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockGoogleSync.watchvalues.WatchSetting
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult

/**
 * Writes the settings of a plugin's [org.avmedia.smartsync.plugin.api.WatchCommand.ChangeSettings], already checked
 * ([WatchSettingRegistry.checkAll]), one write at a time. [ready] is the link's own state (connected, and for the
 * DW-H5600 family its setup done), read at each call: nothing here waits for an event it could have missed. Not ready
 * within the wait: NOT_CONNECTED, which the [CommandScheduler] keeps for the next connection when the plugin asked so.
 * Android-free.
 */
class HostSettingsWriter(
    private val write: suspend (WatchSetting, Any) -> SettingWriteResult,
    private val ready: () -> Boolean,
    /** After a write: [key] now has [value] (a setting changed event). */
    private val onWritten: (key: String, value: Any) -> Unit = { _, _ -> },
    /** Once after the writes of a call that wrote something (the values are read back). */
    private val afterWrites: () -> Unit = {},
    private val log: (who: String, line: String) -> Unit = { _, _ -> },
    private val pollMs: Long = 200,
) {
    private val mutex = Mutex()

    suspend fun apply(who: String, values: Map<String, Any>, readyWaitMs: Long = READY_WAIT_MS): WriteResult {
        if (values.isEmpty()) return PluginResult.Failed(FailReason.INVALID, "no setting named")
        val isReady = withTimeoutOrNull(readyWaitMs) { while (!ready()) delay(pollMs); true } ?: false
        if (!isReady) {
            log(who, "settings ${values.keys.joinToString()} not written now: the watch is not ready")
            return CommandScheduler.NOT_CONNECTED
        }
        return mutex.withLock {
            val failures = ArrayList<String>()
            var wrote = false
            for ((key, value) in values) {
                val setting = WatchSettingRegistry.find(key) ?: continue
                val result = runCatching { write(setting, value) }.getOrElse { SettingWriteResult.Failed("the write failed") }
                when (result) {
                    SettingWriteResult.Done, SettingWriteResult.Written, SettingWriteResult.Unchanged -> {
                        log(who, "set $key to $value")
                        wrote = true
                        onWritten(key, value)
                    }
                    is SettingWriteResult.Failed -> {
                        log(who, "set $key to $value failed: ${result.message}")
                        failures += "$key: ${result.message}"
                    }
                }
            }
            if (wrote) afterWrites()
            if (failures.isEmpty()) CommandScheduler.DONE else PluginResult.Failed(FailReason.INTERNAL, failures.joinToString("; "))
        }
    }

    companion object {
        /** The watch's setup after a connection takes a few seconds (`47 01` about 5 s after the link). */
        const val READY_WAIT_MS = 15_000L

        /** Ready for writes: connected, and a watch of the DW-H5600 family also sent `47 01` on this link. */
        fun linkReady(connected: Boolean, h5600Family: Boolean, setupDone: Boolean): Boolean = connected && (!h5600Family || setupDone)
    }
}
