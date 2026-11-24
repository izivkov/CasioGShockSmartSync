package org.avmedia.smartsync.homeassistant

import java.time.ZoneId
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
    fun whileOnThePluginAsksForUndecodedSettings() {
        assertFalse(HaRuntime.needsFor(configured = true, groups = HaGroup.entries.toSet(), rawOn = false).undecodedSettings)
        assertTrue(HaRuntime.needsFor(configured = true, groups = HaGroup.entries.toSet(), rawOn = true).undecodedSettings)
        assertFalse("not set up: nothing", HaRuntime.needsFor(configured = false, groups = HaGroup.entries.toSet(), rawOn = true).undecodedSettings)
    }

    /** Background health reads only while Home Assistant is set up with its Health group on (was HealthReadScheduler.hasConsumer). */
    @Test
    fun healthReadsOnlyForTheHealthGroup() {
        assertTrue(HaRuntime.needsFor(true, setOf(HaGroup.HEALTH), false).healthReads)
        assertFalse(HaRuntime.needsFor(false, setOf(HaGroup.HEALTH), false).healthReads)
        assertFalse(HaRuntime.needsFor(true, setOf(HaGroup.WATCH), false).healthReads)
    }
}
