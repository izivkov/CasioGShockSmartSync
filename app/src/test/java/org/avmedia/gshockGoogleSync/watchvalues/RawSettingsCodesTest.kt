package org.avmedia.gshockGoogleSync.watchvalues

import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The raw codes are read only while a plugin asks for undecoded settings (Home Assistant's raw switch). */
class RawSettingsCodesTest {
    @Test
    fun noRawCodeIsReadWhileOffOnlyTheDecodedOnes() {
        assertEquals(RawSettings.DECODED_CODES, RawSettings.codesFor(false))
        assertEquals(RawSettings.CODES, RawSettings.codesFor(true))
        assertTrue(RawSettings.CODES.containsAll(RawSettings.DECODED_CODES))
        assertFalse(0x52 in RawSettings.codesFor(false))
    }

    @Test
    fun theRegistryAsksForNoRawCodeWhileOff() = runBlocking {
        val asked = mutableListOf<Int>()
        fun env(on: Boolean) = WatchVariables.Env(
            api = io.mockk.mockk(relaxed = true),
            features = io.mockk.mockk(relaxed = true),
            raw = RawRequester { code, _ -> asked += code; emptyList() },
            isH5600 = { true },
            phoneBattery = { 50 },
            lastSync = { null },
            rawOn = { on },
        )
        WatchVariables.registry(env(false)).resolve(VariableContext("test", OffsetDateTime.now(), true))
        assertTrue("asked $asked", RawSettings.DECODED_CODES.containsAll(asked.filter { it != 0x28 && it != 0x20 }))
    }
}
