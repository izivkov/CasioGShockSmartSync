package org.avmedia.gshockapi

import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model table itself maps the H5600 platform; the app no longer re-resolves names. */
class WatchInfoModelTableTest {
    @After fun clean() = WatchInfo.reset()

    @Test fun gbxH5600ResolvesToTheH5600ModelAndProtocol() {
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertEquals(WatchInfo.WatchModel.DW_H5600, WatchInfo.model)
        assertTrue(WatchInfo.protocol is H5600Protocol)
        assertEquals("CASIO GBX-H5600", WatchInfo.name)
    }

    @Test fun gbxH5600IsAlwaysConnected() {
        assertTrue(WatchInfo.isAlwaysConnected("GBX-H5600"))
    }
}
