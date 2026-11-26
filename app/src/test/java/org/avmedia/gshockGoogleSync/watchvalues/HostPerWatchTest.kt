package org.avmedia.gshockGoogleSync.watchvalues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host keeps what it reads per watch: another watch never gets a value of this one. */
class HostPerWatchTest {
    private val gbx = "gshock-aaaaaaaaaa01"
    private val ocw = "gshock-bbbbbbbbbb02"

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
