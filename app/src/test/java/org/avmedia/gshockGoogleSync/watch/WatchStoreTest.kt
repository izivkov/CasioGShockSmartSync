package org.avmedia.gshockGoogleSync.watch

import org.avmedia.gshockGoogleSync.history.HistoryDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every stored value is kept per watch: switching watches never shows the other watch's values. */
class WatchStoreTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private val a = WatchKeys.of("AA:AA:AA:AA:AA:01")
    private val b = WatchKeys.of("BB:BB:BB:BB:BB:02")
    private val kv = Mem()
    private var now = 1_000L
    private val store = WatchStore(kv, clock = { now }, pickLegacyOwner = { a })

    @Test fun watchBSeesNoneOfAsValuesAndABackSeesAllOfThem() {
        store.put(a, "battery", "83")
        store.put(a, "ha.sent", "{}")
        store.put(a, "health.last_read.at", "2026-11-24T10:00")
        assertNull(store.get(b, "battery"))
        assertNull(store.get(b, "ha.sent"))
        assertNull(store.get(b, "health.last_read.at"))
        store.put(b, "battery", "40")
        assertEquals("83", store.get(a, "battery")?.value)
        assertEquals("{}", store.get(a, "ha.sent")?.value)
        assertEquals("2026-11-24T10:00", store.get(a, "health.last_read.at")?.value)
        assertEquals("40", store.get(b, "battery")?.value)
        assertNull("no watch, no value", store.get("", "battery"))
    }

    @Test fun aValueKnowsItsWatchAndWhenItWasStored() {
        now = 5_000L
        store.put(a, "battery", "83")
        val v = store.get(a, "battery")!!
        assertEquals(a, v.watch)
        assertEquals(5_000L, v.atMs)
        assertEquals(false, v.confirmed)
    }

    @Test fun theValuesOfAnOlderVersionGoToTheLegacyOwnerOnce() {
        kv.m["ha.sent"] = "old"
        assertNull("another watch never gets them", store.get(b, "ha.sent", legacyKey = "ha.sent"))
        assertEquals("old", store.get(a, "ha.sent", legacyKey = "ha.sent")?.value)
        assertNull("moved, not copied", kv.m["ha.sent"])
        assertEquals("old", store.get(a, "ha.sent", legacyKey = "ha.sent")?.value)
        assertNull(store.get(b, "ha.sent", legacyKey = "ha.sent"))
    }

    @Test fun withoutARegisteredWatchTheFirstWatchThatReadsIsTheLegacyOwner() {
        val s = WatchStore(kv, pickLegacyOwner = { null })
        kv.m["old"] = "x"
        assertEquals("x", s.get(b, "v", legacyKey = "old")?.value)
        assertEquals(b, s.legacyOwner(a))
    }

    @Test fun aReParedWatchTakesAllItsValuesToItsNewKey() {
        store.put(a, "battery", "83")
        store.put(a, "phone_notifications.mode", "SELECTED")
        store.put(b, "battery", "40")
        val c = WatchKeys.of("CC:CC:CC:CC:CC:03")
        store.legacyOwner(a)
        store.move(a, c)
        assertNull(store.get(a, "battery"))
        assertEquals("83", store.get(c, "battery")?.value)
        assertEquals("SELECTED", store.get(c, "phone_notifications.mode")?.value)
        assertEquals("40", store.get(b, "battery")?.value)
        assertTrue(store.isLegacyOwner(c))
    }

    @Test fun eachWatchHasItsOwnHistoryFileAndTheOldFileIsTheLegacyOwners() {
        assertEquals(HistoryDb.NAME, HistoryDb.fileFor(store, a, legacyFileExists = true))
        val other = HistoryDb.fileFor(store, b, legacyFileExists = true)
        assertNotEquals(HistoryDb.NAME, other)
        assertEquals("kept: the same file next time", other, HistoryDb.fileFor(store, b, legacyFileExists = true))
        assertNotEquals("no watch never opens a watch's history", HistoryDb.NAME, HistoryDb.fileFor(store, "", legacyFileExists = true))
    }

    /** One helper everywhere: a value stored for A never reaches B, and stays marked until confirmed. */
    @Test fun aStoredValueReachesOnlyItsOwnWatchAndStaysMarkedUntilConfirmed() {
        val v = StoredValue("83", a, atMs = 0L)
        assertNull(StoredValue.valueFor(v, b))
        assertEquals("83", StoredValue.valueFor(v, a))
        val hour = 3_600_000L
        val stored = StoredValue("83", a, atMs = 1 * hour)
        assertEquals("updating", stored.mark(3 * hour, reading = true))
        assertEquals("stored, 2 h ago", stored.mark(3 * hour, reading = false))
        assertEquals("stored", StoredValue("83", a, 0L).mark(3 * hour, reading = false))
        assertNull(stored.confirm(3 * hour).mark(4 * hour, reading = false))
        assertEquals("just now", StoredValue.age(10_000))
        assertEquals("5 min ago", StoredValue.age(5 * 60_000L))
        assertEquals("3 days ago", StoredValue.age(72 * hour))
    }
}
