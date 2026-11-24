package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The host's settings writer for plugin commands. It used to be made at the plugin's first command, long after the
 * watch's setup (`47 01`): a watch face set from Home Assistant while the watch was connected and set up failed with
 * "the watch is not ready" because the writer had missed the setup event. Readiness is the link's own state now.
 */
class HostSettingsReadinessTest {
    private val written = ArrayList<Pair<String, Any>>()
    private val logged = ArrayList<String>()
    private var after = 0
    private var result: SettingWriteResult = SettingWriteResult.Done

    private fun writer(ready: () -> Boolean = { true }) = HostSettingsWriter(
        write = { s, v -> written += s.key to v; result }, ready = ready,
        afterWrites = { after++ }, log = { who, l -> logged += "$who $l" }, pollMs = 5,
    )

    @Test fun aWriterMadeAfterTheWatchsSetupWritesAtOnce() = runBlocking {
        assertEquals(CommandScheduler.DONE, writer().apply("Home Assistant", mapOf("watch_face" to "steps"), readyWaitMs = 300))
        assertEquals(listOf<Pair<String, Any>>("watch_face" to "steps"), written)
        assertEquals(listOf("Home Assistant set watch_face to steps"), logged)
        assertEquals(1, after)
    }

    @Test fun notReadyWithinTheWaitIsNotConnectedAndWritesNothing() = runBlocking {
        val r = writer(ready = { false }).apply("Home Assistant", mapOf("watch_face" to "steps"), readyWaitMs = 50) as PluginResult.Failed
        assertEquals("the scheduler keeps it for the next connection", FailReason.NOT_CONNECTED, r.reason)
        assertTrue(written.isEmpty())
    }

    @Test fun theWatchGettingReadyDuringTheWaitIsWritten() = runBlocking {
        var polls = 0
        assertEquals(CommandScheduler.DONE, writer(ready = { ++polls > 3 }).apply("x", mapOf("step_goal" to 9000), readyWaitMs = 2_000))
        assertEquals(1, written.size)
    }

    @Test fun aFailedWriteIsOneLineAndAFailureAndNothingIsReadBack() = runBlocking {
        result = SettingWriteResult.Failed("The watch did not answer. Nothing was changed.")
        val r = writer().apply("Home Assistant", mapOf("watch_face" to "steps")) as PluginResult.Failed
        assertEquals(FailReason.INTERNAL, r.reason)
        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("failed"))
        assertEquals(0, after)
    }

    /** Connected and, for the DW-H5600 family, `47 01` on this link; other watches need no setup. */
    @Test fun readyIsConnectedAndForTheH5600FamilyTheSetupDone() {
        assertTrue(HostSettingsWriter.linkReady(connected = true, h5600Family = true, setupDone = true))
        assertFalse(HostSettingsWriter.linkReady(connected = true, h5600Family = true, setupDone = false))
        assertTrue(HostSettingsWriter.linkReady(connected = true, h5600Family = false, setupDone = false))
        assertFalse(HostSettingsWriter.linkReady(connected = false, h5600Family = false, setupDone = true))
    }
}
