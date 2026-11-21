package org.avmedia.gshockGoogleSync.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWindowTest {
    private val ecb = "C5:E8:08:F0:C7:81"
    private val h5600 = "C5:E2:11:1A:FD:F5"

    @Test
    fun aConnectionInsideTheWindowUsesItUpOnce() {
        SetupWindow.open(ecb, now = 1_000L)
        assertTrue(SetupWindow.consume(ecb, now = 2_000L))
        assertFalse(SetupWindow.consume(ecb, now = 3_000L))
    }

    @Test
    fun aConnectionAfterTheWindowDoesNotUseIt() {
        SetupWindow.open(ecb, now = 1_000L)
        assertFalse(SetupWindow.consume(ecb, now = 1_000L + 3 * 60_000L))
    }

    @Test
    fun aWindowForOneWatchDoesNotOpenAnother() {
        SetupWindow.open(ecb, now = 1_000L)
        assertFalse(SetupWindow.consume(h5600, now = 2_000L))
        assertTrue(SetupWindow.consume(ecb, now = 2_000L))
    }
}
