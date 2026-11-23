package org.avmedia.gshockapi

import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model table itself maps the H5600 platform; the app no longer re-resolves names. */
class WatchInfoModelTableTest {
    @Test fun gbxH5600ResolvesToTheH5600ModelAndProtocol() {
        val (model, protocol) = WatchInfo.resolvedFor("CASIO GBX-H5600")
        assertEquals(WatchInfo.WatchModel.DW_H5600, model)
        assertTrue(protocol is H5600Protocol)
    }

    @Test fun gbxH5600IsAlwaysConnected() {
        assertTrue(WatchInfo.isAlwaysConnected("GBX-H5600"))
    }
}
