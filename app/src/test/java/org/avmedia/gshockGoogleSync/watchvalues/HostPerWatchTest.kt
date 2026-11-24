package org.avmedia.gshockGoogleSync.watchvalues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host keeps what it reads and writes per watch: another watch never gets a setting or a value of this one. */
class HostPerWatchTest {
    private val gbx = "gshock-aaaaaaaaaa01"
    private val ocw = "gshock-bbbbbbbbbb02"

    @Test fun watchSettingsAskedForOneWatchAreNeverWrittenToAnother() = kotlinx.coroutines.runBlocking {
        var connected = gbx
        val written = ArrayList<String>()
        val logs = ArrayList<String>()
        val c = WatchSettingCommands(
            write = { s, _ -> written += s.key; org.avmedia.gshockapi.io.SettingWriteResult.Done },
            connected = { true }, log = { logs += it }, onQueued = {}, watch = { connected },
        )
        c.submit(mapOf("setting" to "watch_face", "value" to "steps"))
        connected = ocw
        c.onSetupDone()
        c.flush()
        assertTrue(written.toString(), written.isEmpty())
        assertTrue(logs.toString(), logs.any { it.contains("dropped") })
    }

    @Test fun rawSettingsAreTheConnectedWatchsOwn() = kotlinx.coroutines.runBlocking {
        var connected = gbx
        val reader = RawSettingsReader(clock = { 0L }, watch = { connected })
        reader.refresh(RawRequester { code, _ -> listOf("0x%02X 01 02".format(code)) }, listOf(0x51))
        assertEquals(1, reader.values().size)
        connected = ocw
        assertTrue("another watch sees none of them", reader.values().isEmpty())
        assertNull(reader.lastReadMillis())
    }
}
