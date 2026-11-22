package org.avmedia.gshockGoogleSync.session

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModelOverrideTest {
    @After
    fun reset() {
        WatchModelOverride.realName = null
    }

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

    @Test
    fun h5600WatchesAreResolvedByTheLibraryAsDwH5600() {
        assertEquals("DW-H5600", WatchModelOverride.effectiveLibraryName("GBX-H5600"))
        assertEquals("DW-H5600", WatchModelOverride.effectiveLibraryName("CASIO GBX-H5600"))
        assertEquals("DW-H5600", WatchModelOverride.effectiveLibraryName("DW-H5600"))
    }

    @Test
    fun otherNamesAreLeftAlone() {
        assertEquals("GW-B5600", WatchModelOverride.effectiveLibraryName("GW-B5600"))
        assertEquals("GBD-H2000", WatchModelOverride.effectiveLibraryName("GBD-H2000"))
    }

    @Test
    fun displayNameKeepsTheRealName() {
        WatchModelOverride.realName = "GBX-H5600"
        assertEquals("GBX-H5600", WatchModelOverride.displayName("DW-H5600"))
        WatchModelOverride.realName = null
        assertEquals("GW-B5600", WatchModelOverride.displayName("GW-B5600"))
    }

    @Test
    fun reResolveKeepsTheWatchAddress() {
        // The library's setNameAndModel drops the address (2026-11-22: the GBX-H5600 then never left the
        // first page, because the Allow connection window is looked up by that address).
        var address = "AA:BB:CC:DD:EE:FF"
        var name = "CASIO GBX-H5600"
        WatchModelOverride.reResolve(
            "DW-H5600",
            setNameAndModel = { name = it; address = "" },
            getAddress = { address },
            setAddress = { address = it },
        )
        assertEquals("DW-H5600", name)
        assertEquals("AA:BB:CC:DD:EE:FF", address)
    }
}
