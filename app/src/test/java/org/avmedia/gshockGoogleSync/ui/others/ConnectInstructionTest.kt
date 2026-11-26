package org.avmedia.gshockGoogleSync.ui.others

import org.avmedia.gshockGoogleSync.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConnectInstructionTest {
    @Test
    fun oceanusWatchesGetTheirOwnInstruction() {
        assertEquals(R.string.ocw_s400_connect_instruction, connectInstructionResId("CASIO OCW-S400"))
        assertEquals(R.string.ocw_s7000_connect_instruction, connectInstructionResId("CASIO OCW-S7000"))
    }

    @Test
    fun otherWatchesKeepTheDefault() {
        assertNull(connectInstructionResId("CASIO GBX-H5600"))
        assertNull(connectInstructionResId("CASIO GW-B5600"))
    }

    @Test
    fun ocwS7000TextNamesButtonBAndTheSecondHand() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(
            strings.contains(
                "<string name=\"ocw_s7000_connect_instruction\">To connect, hold (B) for 2.5 s, until the second hand points to [C]</string>"
            )
        )
    }
}
