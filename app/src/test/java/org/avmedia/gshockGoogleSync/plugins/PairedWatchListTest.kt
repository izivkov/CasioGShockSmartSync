package org.avmedia.gshockGoogleSync.plugins

import org.junit.Assert.assertEquals
import org.junit.Test

/** The watches the host gives plugins: a watch whose data moved to its re-paired id is listed once, under the new id. */
class PairedWatchListTest {
    private val kv = object : org.avmedia.gshockGoogleSync.watch.KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }
    private val old = "gshock-aabbccddeeff"
    private val new = "gshock-c51422f1e489"

    @Test fun aWatchWhoseDataMovedIsNotListedUnderItsOldId() {
        PairedWatchList.moved(kv, old, new)
        assertEquals(listOf(new), PairedWatchList.visible(listOf(old, new), connected = new, kv = kv))
    }

    @Test fun theOldIdComesBackWhenThatWatchConnectsUnderIt() {
        PairedWatchList.moved(kv, old, new)
        assertEquals(listOf(old, new), PairedWatchList.visible(listOf(old, new), connected = old, kv = kv))
        PairedWatchList.seen(kv, old)
        assertEquals(listOf(old, new), PairedWatchList.visible(listOf(old, new), connected = null, kv = kv))
    }
}
