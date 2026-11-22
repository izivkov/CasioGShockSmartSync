package org.avmedia.gshockapi.casio

import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationSubscriptionsTest {
    private val ch = UUID.fromString("26eb002d-b012-49a8-b1f8-394fb2032b0f")
    private var now = 1_700_000_000_000L

    @Before fun setUp() {
        ConnectionKeepAlive.clock = { now }
        ConnectionKeepAlive.clearEvents()
        NotificationSubscriptions.clear()
    }

    @After fun restore() { ConnectionKeepAlive.clock = System::currentTimeMillis }

    @Test fun aReconnectSubscribesAgain() {
        NotificationSubscriptions.beginDiscovery()
        assertTrue(NotificationSubscriptions.claim(ch))
        assertFalse(NotificationSubscriptions.claim(ch))
        NotificationSubscriptions.beginDiscovery() // drop and reconnect: new discovery
        assertTrue(NotificationSubscriptions.claim(ch))
    }

    @Test fun firstTwoBytesAreLoggedOnlyInTheFirst90Seconds() {
        NotificationSubscriptions.beginDiscovery()
        now += 5_000
        NotificationSubscriptions.logNotification(ch, "0x49 00 12 34")
        now += 90_000
        NotificationSubscriptions.logNotification(ch, "0x49 00")
        val log = ConnectionKeepAlive.recentEvents()
        assertEquals(1, log.size)
        assertTrue(log[0], log[0].endsWith("rx +5s 002d: 49 00"))
    }
}
