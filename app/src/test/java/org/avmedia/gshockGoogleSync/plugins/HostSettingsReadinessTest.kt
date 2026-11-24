package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingCommands
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.smartsync.plugin.api.Delivered
import org.avmedia.smartsync.plugin.api.PluginResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The host makes a plugin's settings writer at the plugin's first command, which comes long after the watch's setup
 * (`47 01`). A watch face set from Home Assistant while the watch was connected and set up failed: the
 * writer said "the watch is not ready" because it had missed the setup event. Readiness is the link's own state.
 */
class HostSettingsReadinessTest {
    @Test fun aWriterMadeAfterTheWatchsSetupWritesAtOnce() = runBlocking {
        val written = ArrayList<String>()
        val late = WatchSettingCommands(
            write = { s, _ -> written += s.key; SettingWriteResult.Done }, connected = { true }, log = {}, onQueued = {},
        )
        val r = late.apply(mapOf("settings" to mapOf("watch_face" to "steps")), readyWaitMs = 300)
        assertEquals(PluginResult.Ok(Delivered.NOW), r)
        assertEquals(listOf("watch_face"), written)
    }
}
