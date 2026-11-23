package org.avmedia.gshockGoogleSync.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModelOverrideTest {
    @Test
    fun h5600FamilyIsAlwaysConnected() {
        assertTrue(WatchModelOverride.isAlwaysConnected("GBX-H5600"))
        assertTrue(WatchModelOverride.isAlwaysConnected("CASIO GBX-H5600"))
        assertTrue(WatchModelOverride.isAlwaysConnected("DW-H5600"))
        assertTrue(WatchModelOverride.isAlwaysConnected("GM-H5600"))
        assertTrue(WatchModelOverride.isAlwaysConnected("ECB-30D"))
    }

    @Test
    fun buttonConnectWatchesAreNot() {
        assertFalse(WatchModelOverride.isAlwaysConnected("GW-B5600"))
        assertFalse(WatchModelOverride.isAlwaysConnected("GBD-H2000"))
        assertFalse(WatchModelOverride.isAlwaysConnected("GA-B2100"))
    }
}
