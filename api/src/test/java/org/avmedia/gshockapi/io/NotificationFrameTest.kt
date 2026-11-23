package org.avmedia.gshockapi.io

import java.time.LocalDateTime
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.NotificationIds
import org.avmedia.gshockapi.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification frame the watch keeps in its list: every frame has its own non-zero id in bytes 0-3 (low byte first),
 * byte 4 = 0 (add, not delete), byte 5 = 1 (alert); the time is 15 zero-padded characters. Before, every frame had id 0
 * and the watch's list stayed empty.
 */
class NotificationFrameTest {
    private fun n(id: Int = 0) = AppNotification(NotificationType.MESSAGE, "20261123T183005", "Charge watch", "", "Battery 25%", id = id)

    @Test fun theIdIsInTheFirstFourBytesLowByteFirst() {
        val f = AppNotificationIO.encodeNotificationPacket(n(0x01020304))
        assertEquals(listOf(4, 3, 2, 1, 0, 1), f.take(6).map { it.toInt() and 0xFF })
        assertEquals(0x01020304, AppNotificationIO.decodeNotificationPacket(f).id)
    }

    @Test fun everySentNotificationGetsItsOwnNonZeroId() {
        val a = NotificationIds.assign(n())
        val b = NotificationIds.assign(n())
        assertNotEquals(0, a.id)
        assertNotEquals(0, b.id)
        assertNotEquals(a.id, b.id)
        val frame = AppNotificationIO.encodeNotificationPacket(a)
        assertTrue(frame.take(4).any { it.toInt() != 0 })
        assertEquals(42, NotificationIds.assign(n(42)).id)
    }

    @Test fun theFrameTimeIsZeroPadded() {
        val t = NotificationIds.timestamp(LocalDateTime.of(2026, 11, 3, 8, 4, 5))
        assertEquals("20261103T080405", t)
        assertEquals(15, t.length)
    }
}
