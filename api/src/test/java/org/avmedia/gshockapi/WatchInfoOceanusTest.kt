package org.avmedia.gshockapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** GENERIC writes the 0x22 scratchpad, which makes the official app forget an Oceanus watch (upstream GShockAPI 38708a0). */
class WatchInfoOceanusTest {
    @Test fun ocwS7000ResolvesToOcwS400WithoutAScratchpad() {
        val info = WatchInfo.infoFor("CASIO OCW-S7000")
        assertEquals(WatchInfo.WatchModel.OCW_S400, info.model)
        assertFalse(info.hasScratchpad)
    }
}
