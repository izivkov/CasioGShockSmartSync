package org.avmedia.gshockGoogleSync.ui.health

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opening the Health screen starts exactly one read; a second open within a minute does not; no Update button. */
class HealthOpenReadTest {
    @Test fun anOpenStartsOneReadAndASecondOpenWithinAMinuteNone() {
        var now = 1_000_000L
        val gate = HealthOpenRead(clock = { now }, running = { false })
        val reads = (1..2).count { gate.tryStart() }
        assertEquals(1, reads)
        now += 59_000
        assertFalse(gate.tryStart())
        now += 2_000
        assertTrue(gate.tryStart())
    }

    @Test fun noReadWhileOneIsRunning() {
        var running = true
        val gate = HealthOpenRead(clock = { 0L }, running = { running })
        assertFalse(gate.tryStart())
        running = false
        assertTrue(gate.tryStart())
    }

    @Test fun theScreenHasNoUpdateButtonAndOpensThroughTheGate() {
        val screen = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/HealthScreen.kt").readText()
        assertFalse("update_latest" in screen)
        assertTrue("HealthAction.ScreenOpened" in screen)
        val vm = File("src/main/java/org/avmedia/gshockGoogleSync/ui/health/HealthViewModel.kt").readText()
        assertTrue("HealthOpenRead.shared.tryStart()" in vm)
        // One open, one read: init no longer starts its own read next to the screen's.
        val init = vm.substringAfter("    init {").substringBefore("    }")
        assertFalse(init, "refreshState()" in init)
    }
}
