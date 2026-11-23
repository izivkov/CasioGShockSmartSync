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

    @Test fun theNotificationManagerNeedsSeventeenValueBytesAfterThe30() {
        assertNull(SettingRequests.tooShort(0x3D, ByteArray(19) { 1 }))
        assertNotNull(SettingRequests.tooShort(0x3D, ByteArray(18) { 1 }))
    }

    @Test fun otherCodesHaveNoMinimum() {
        assertNull(SettingRequests.tooShort(0x51, byteArrayOf(0x51, 1)))
    }
}
