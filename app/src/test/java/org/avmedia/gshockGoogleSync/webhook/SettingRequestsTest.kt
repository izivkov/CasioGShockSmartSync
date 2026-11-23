package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockGoogleSync.ui.watchhealth.SettingRequests
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What a settings read sends (notes D2) and which answers are too short to decode. */
class SettingRequestsTest {
    @Test fun theNotificationManagerIsReadAs3D30AndNeverBare() {
        assertArrayEquals(byteArrayOf(0x3D, 0x30), SettingRequests.forCode(0x3D))
    }

    @Test fun everyOtherCodeIsReadWithTheCodeAlone() {
        assertArrayEquals(byteArrayOf(0x45), SettingRequests.forCode(0x45))
        assertArrayEquals(byteArrayOf(0x42), SettingRequests.forCode(0x42))
    }

    @Test fun theProfileNeedsNineValueBytesAndTheSoundSettingSix() {
        assertNull(SettingRequests.tooShort(0x45, ByteArray(10) { 1 }))
        assertNotNull(SettingRequests.tooShort(0x45, ByteArray(9) { 1 }))
        assertNull(SettingRequests.tooShort(0x42, ByteArray(7) { 1 }))
        val short = SettingRequests.tooShort(0x42, byteArrayOf(0x42, 1, 2))!!
        assertEquals(true, short.contains("42 01 02"))
    }

    // Owner's watch (2026-11-23): 3D 30 + 16 value bytes. The notes expected 17; the real answer is the minimum.
    @Test fun theNotificationManagerNeedsSixteenValueBytesAfterThe30() {
        val real = "3D 30 00 02 01 00 03 00 2F 28 00 28 00 28 00 FA 00 00".split(' ').map { it.toInt(16).toByte() }.toByteArray()
        assertNull(SettingRequests.tooShort(0x3D, real))
        assertNotNull(SettingRequests.tooShort(0x3D, real.copyOf(17)))
    }

    // Owner's watch: 42 0A FF 7F FF 7F, five value bytes, no sound pattern byte.
    @Test fun theSoundSettingAcceptsFiveValueBytes() {
        val real = byteArrayOf(0x42, 0x0A, 0xFF.toByte(), 0x7F, 0xFF.toByte(), 0x7F)
        assertNull(SettingRequests.tooShort(0x42, real))
    }

    @Test fun otherCodesHaveNoMinimum() {
        assertNull(SettingRequests.tooShort(0x51, byteArrayOf(0x51, 1)))
    }
}
