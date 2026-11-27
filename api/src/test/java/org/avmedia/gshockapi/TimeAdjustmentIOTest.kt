package org.avmedia.gshockapi

import org.avmedia.gshockapi.io.TimeAdjustmentIOFunctional
import org.avmedia.gshockapi.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeAdjustmentIOTest {
    private fun settings(vararg bytes: Int) = Utils.fromByteArrayToHexStr(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }

    @Test
    fun turningSyncOffAndOnChangesOnlyBit7() {
        // Byte 12 bits 0-6 hold other data (here: hour 6); they must survive both ways.
        val on = settings(0x11, 0x0f, 0x0f, 0x0f, 0x06, 0x00, 0x50, 0x00, 0x04, 0x00, 0x01, 0x00, 0x06, 0x1e, 0x03)
        val off = TimeAdjustmentIOFunctional.encode(on, timeAdjustment = false, adjustmentTimeMinutes = 30).getOrThrow()
        assertEquals("11 0f 0f 0f 06 00 50 00 04 00 01 00 86 1e 03", off.hex())
        assertFalse(TimeAdjustmentIOFunctional.parseIsTimeAdjustmentSet(Utils.fromByteArrayToHexStr(off)))

        val backOn = TimeAdjustmentIOFunctional.encode(Utils.fromByteArrayToHexStr(off), true, 30).getOrThrow()
        assertEquals("11 0f 0f 0f 06 00 50 00 04 00 01 00 06 1e 03", backOn.hex())
        assertTrue(TimeAdjustmentIOFunctional.parseIsTimeAdjustmentSet(Utils.fromByteArrayToHexStr(backOn)))
    }

    @Test
    fun watchesUsingOnly00And80GetTheSameBytesAsBefore() {
        val on = settings(0x11, 0x0f, 0x0f, 0x0f, 0x06, 0x00, 0x50, 0x00, 0x04, 0x00, 0x01, 0x00, 0x00, 0x37, 0xd2)
        assertEquals(
            "11 0f 0f 0f 06 00 50 00 04 00 01 00 80 1e d2",
            TimeAdjustmentIOFunctional.encode(on, false, 30).getOrThrow().hex(),
        )
        assertEquals(
            "11 0f 0f 0f 06 00 50 00 04 00 01 00 00 1e d2",
            TimeAdjustmentIOFunctional.encode(on, true, 30).getOrThrow().hex(),
        )
    }
}
