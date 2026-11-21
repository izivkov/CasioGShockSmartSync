package org.avmedia.gshockGoogleSync.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupWindowTest {
    @Test
    fun aConnectionInsideTheWindowUsesItUpOnce() {
        SetupWindow.open(now = 1_000L)
        assertTrue(SetupWindow.consume(now = 2_000L))
        assertFalse(SetupWindow.consume(now = 3_000L))
    }

    @Test
    fun aConnectionAfterTheWindowDoesNotUseIt() {
        SetupWindow.open(now = 1_000L)
        assertFalse(SetupWindow.consume(now = 1_000L + 3 * 60_000L))
    }
}
