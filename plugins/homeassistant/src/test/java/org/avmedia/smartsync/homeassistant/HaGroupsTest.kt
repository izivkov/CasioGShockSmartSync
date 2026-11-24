package org.avmedia.smartsync.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaGroupsTest {
    private fun sensor(id: String) = HaSensors.ALL.first { it.uniqueId == id }

    @Test fun everySensorHasAGroup() {
        assertEquals(HaSensors.ALL.size, HaSensors.ALL.map { HaGroups.of(it) }.size)
        assertEquals(HaGroup.entries.toSet(), HaSensors.ALL.map { HaGroups.of(it) }.toSet())
    }

    @Test fun groupsFollowTheValues() {
        assertEquals(HaGroup.WATCH, HaGroups.of(sensor("battery")))
        assertEquals(HaGroup.WATCH, HaGroups.of(sensor("watch_firmware")))
        assertEquals(HaGroup.HEALTH, HaGroups.of(sensor("steps_today")))
        assertEquals(HaGroup.HEALTH, HaGroups.of(sensor("health_recorded_at")))
        assertEquals(HaGroup.SETTINGS, HaGroups.of(sensor("watch_settings")))
    }

    @Test fun aSwitchedOffGroupSendsNothing() {
        val ids = HaGroups.sensors(setOf(HaGroup.WATCH)).map { it.uniqueId }
        assertTrue("battery" in ids)
        assertFalse("steps_today" in ids)
        assertFalse("watch_settings" in ids)
        assertTrue(HaGroups.sensors(emptySet()).isEmpty())
    }

    // One health switch; everything else is always sent. The old health group switch keeps its state.
    @Test fun onlyHealthCanBeSwitchedOffAndItsOldStateStays() {
        val mem = HaMemoryStore()
        assertEquals(HaGroup.entries.toSet(), HaStore(mem).groupsOn())
        mem.put("group.SETTINGS", "off") // an old per-group switch: no longer one
        assertEquals(HaGroup.entries.toSet(), HaStore(mem).groupsOn())
        HaStore(mem).setHealthOn(false)
        assertEquals(setOf(HaGroup.WATCH, HaGroup.SETTINGS), HaStore(mem).groupsOn())
        HaStore(mem).setHealthOn(true)
        assertTrue(HaStore(mem).healthOn())
        val owner = HaMemoryStore().apply { put("group.HEALTH", "on") }
        assertTrue("a health group that was on: the switch is on", HaStore(owner).healthOn())
    }
}
