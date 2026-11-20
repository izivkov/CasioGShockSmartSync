package org.avmedia.gshockGoogleSync.pairing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlwaysConnectedConnectionPolicyTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun watchThatIsNotAlwaysConnectedIsAlwaysAllowed() {
        assertTrue(AlwaysConnectedConnectionPolicy.decide(false, false, now - 1000, now))
    }

    @Test
    fun firstConnectionIsAllowed() {
        assertTrue(AlwaysConnectedConnectionPolicy.decide(true, false, null, now))
    }

    @Test
    fun connectionWithinSixHoursIsDenied() {
        assertFalse(AlwaysConnectedConnectionPolicy.decide(true, false, now - 5 * hour, now))
    }

    @Test
    fun connectionAfterSixHoursIsAllowed() {
        assertTrue(AlwaysConnectedConnectionPolicy.decide(true, false, now - 7 * hour, now))
    }

    @Test
    fun allowOnceOverridesTheLimit() {
        assertTrue(AlwaysConnectedConnectionPolicy.decide(true, true, now - hour, now))
    }
}
