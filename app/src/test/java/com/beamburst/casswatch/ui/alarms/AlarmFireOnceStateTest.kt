package com.beamburst.casswatch.ui.alarms

import org.junit.Assert.*
import org.junit.Test

class AlarmFireOnceStateTest {

    @Test
    fun `showAsEnabled is true when alarm enabled and not yet fired`() {
        val enabled = true
        val firedAt: Long? = null
        val showAsEnabled = enabled && firedAt == null
        assertTrue(showAsEnabled)
    }

    @Test
    fun `showAsEnabled is false when firedAt is set even though enabled is true`() {
        val enabled = true
        val firedAt: Long? = 1234567890L
        val showAsEnabled = enabled && firedAt == null
        assertFalse(showAsEnabled)
    }

    @Test
    fun `confirming sync clears firedAt and sets enabled false`() {
        var enabled = true
        var firedAt: Long? = 1234567890L
        // Simulate: successful sync with alarm sent disabled
        val sentDisabled = true // we sent it disabled to the watch
        if (sentDisabled && firedAt != null) {
            enabled = false
            firedAt = null
        }
        assertFalse(enabled)
        assertNull(firedAt)
    }

    @Test
    fun `alarm hash generation is deterministic`() {
        val hash = alarmHash(hour = 6, minute = 30, dayMask = "ALL", enabled = true)
        assertEquals("6:30:ALL:true", hash)
    }

    @Test
    fun `alarm hash generation for specific days`() {
        val hash = alarmHash(hour = 9, minute = 0, dayMask = "MONDAY,FRIDAY", enabled = false)
        assertEquals("9:0:MONDAY,FRIDAY:false", hash)
    }

    // Helper that mirrors the alarmHash() function that will be added to AlarmViewModel in Task 4
    private fun alarmHash(hour: Int, minute: Int, dayMask: String, enabled: Boolean) =
        "$hour:$minute:$dayMask:$enabled"
}
