package org.avmedia.gshockGoogleSync.webhook

import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawReadSwitchTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    private var now = 1_700_000_000_000L
    private val sw = RawReadSwitch(Mem(), { now })

    @Test
    fun offByDefault() {
        assertFalse(sw.isOn())
        assertNull(sw.onUntilText())
    }

    @Test
    fun turnsItselfOffAfter24Hours() {
        sw.turnOn()
        assertTrue(sw.isOn())
        now += 24 * 3_600_000L - 1
        assertTrue(sw.isOn())
        now += 1
        assertFalse(sw.isOn())
        assertNull(sw.onUntilText())
    }

    @Test
    fun showsUntilWhenOnAndCanBeTurnedOff() {
        sw.turnOn()
        val expected = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .format(java.time.Instant.ofEpochMilli(now + 24 * 3_600_000L).atZone(ZoneId.of("UTC")))
        assertEquals(expected, sw.onUntilText(ZoneId.of("UTC")))
        sw.turnOff()
        assertFalse(sw.isOn())
    }

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
