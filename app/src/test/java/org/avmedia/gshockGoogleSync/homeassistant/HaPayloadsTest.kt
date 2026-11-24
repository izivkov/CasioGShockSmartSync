package org.avmedia.gshockGoogleSync.homeassistant


import org.avmedia.gshockGoogleSync.watchvalues.RawSettings
import org.avmedia.gshockGoogleSync.watchvalues.WatchDecoders
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaPayloadsTest {
    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun firmwareIsNotGuessedFromTheOwnersVersionAnswer() {
        val answer = "0x20 23 07 00 12 00 00 00 00 00 00 00 00 04 02 01 00 09 00 00"
        assertEquals("23 07 00 12 00 00 00 00 00 00 00 00 04 02 01 00 09 00 00", WatchDecoders.versionRaw(answer))
        assertEquals("unknown", WatchDecoders.firmwareState(answer))
        assertEquals(null, WatchDecoders.versionRaw("0x28 06 1D"))
        assertEquals(null, WatchDecoders.versionRaw(null))
        assertEquals(null, WatchDecoders.firmwareState("0x20"))
    }

    @Test
    fun faceAnswersAreKeptAsHexWithoutThe0xPrefixTheGuardDrops() {
        assertEquals("51 01 00 FF", WatchDecoders.rawAnswer("0x51 01 00 ff", 0x51))
        assertEquals("52 0A", WatchDecoders.rawAnswer("0x52 0A", 0x52))
        assertFalse(WatchDecoders.looksLikeRawHex(WatchDecoders.rawAnswer("0x51 01 00 ff", 0x51)!!))
        // wrong code, no data bytes, not hex, nothing: unknown, never a guess
        assertEquals(null, WatchDecoders.rawAnswer("0x52 01", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer("0x51", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer("0x51 zz", 0x51))
        assertEquals(null, WatchDecoders.rawAnswer(null, 0x51))
    }

    private fun fakeEnv(): WatchVariables.Env = WatchVariables.Env(
        api = io.mockk.mockk(relaxed = true),
        features = io.mockk.mockk(relaxed = true),
        raw = io.mockk.mockk(relaxed = true),
        isH5600 = { true },
        phoneBattery = { 50 },
        lastSync = { null },
    )

}
