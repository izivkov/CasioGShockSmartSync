package org.avmedia.gshockGoogleSync.homeassistant

import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.io.SettingWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSettingCommandsTest {
    private val written = ArrayList<Pair<String, Any>>()
    private val logged = ArrayList<String>()
    private var connected = true
    private var after = 0
    private var result: SettingWriteResult = SettingWriteResult.Done
    private val commands = WatchSettingCommands(
        write = { s, v -> written += s.key to v; result },
        connected = { connected },
        afterWrites = { after++ },
        log = { logged += it },
        onQueued = {},
    )

    private fun one(key: String, value: Any?) = mapOf<String, Any?>("setting" to key, "value" to value)

    @Test fun aValidValueIsWrittenOnceAfterSetupAndLogged() = runBlocking {
        commands.submit(one("watch_face", "steps"))
        commands.onSetupDone()
        commands.flush()
        commands.flush()
        assertEquals(listOf("watch_face" to "steps"), written)
        assertEquals(listOf("Home Assistant set watch_face to steps"), logged)
        assertEquals(1, after)
    }

    @Test fun nothingIsWrittenBeforeSetupIsDoneOrWhileDisconnected() = runBlocking {
        commands.submit(one("watch_face", "steps"))
        commands.flush()
        assertTrue(written.isEmpty())
        commands.onSetupDone()
        connected = false
        commands.flush()
        assertTrue(written.isEmpty())
        assertEquals(listOf("watch_face"), commands.pendingKeys())
        connected = true
        commands.onDisconnected()
        commands.flush()
        assertTrue("a new link needs its own setup", written.isEmpty())
        commands.onSetupDone()
        commands.flush()
        assertEquals(1, written.size)
    }

    @Test fun whileDisconnectedTheNewerValueReplacesTheOlderOne() = runBlocking {
        connected = false
        commands.submit(one("watch_face", "steps"))
        commands.submit(one("watch_face", "time_only"))
        commands.submit(one("step_goal", 9000))
        assertEquals(listOf("watch_face", "step_goal"), commands.pendingKeys())
        connected = true
        commands.onSetupDone()
        commands.flush()
        assertEquals(listOf("watch_face" to "time_only", "step_goal" to 9000), written)
    }

    @Test fun aListOfSettingsIsAccepted() = runBlocking {
        commands.submit(mapOf("settings" to mapOf("watch_face" to "tide", "smart_alarm" to false)))
        commands.onSetupDone()
        commands.flush()
        assertEquals(listOf("watch_face" to "tide", "smart_alarm" to false), written)
    }

    @Test fun unknownKeyAndBadValueAreOneLogLineEachAndNothingIsQueued() {
        commands.submit(one("time", "12:00"))
        commands.submit(one("step_goal", 5))
        commands.submit(mapOf("value" to 1))
        assertEquals(3, logged.size)
        assertTrue(logged[0].contains("unknown setting time"))
        assertTrue(logged[1].contains("step_goal") && logged[1].contains("range"))
        assertTrue(commands.pendingKeys().isEmpty())
    }

    @Test fun aFailedWriteIsLoggedOnceAndNotRetried() = runBlocking {
        result = SettingWriteResult.Failed("The watch did not answer. Nothing was changed.")
        commands.submit(one("watch_face", "steps"))
        commands.onSetupDone()
        commands.flush()
        commands.flush()
        assertEquals(1, written.size)
        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("failed"))
        assertEquals(0, after)
    }
}
