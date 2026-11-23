package org.avmedia.gshockapi.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit 2 and the own points of request 0x2E, and the text form of an own point. */
class TideCodecsTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private val unit2 = bytes(0x2E, 2, 4, 0x34, 0x12, 0x78, 0x56, 0x01, 0xAB)

    @Test
    fun unit2IsReadFromIndexZeroToFive() {
        val s = TideSettingCodec.parse(unit2)!!
        assertEquals(4, s.source)
        assertEquals(0x1234, s.presetPort)
        assertEquals(0x5678, s.appPort)
        assertTrue(s.feet)
    }

    @Test
    fun unit2IsRefusedWhenShortOrForAnotherUnit() {
        assertNull(TideSettingCodec.parse(bytes(0x2E, 2, 0, 0, 0, 0, 0)))
        assertNull(TideSettingCodec.parse(bytes(0x2E, 3, 0, 0, 0, 0, 0, 0)))
        assertNull(TideSettingCodec.parse(bytes(0x2F, 2, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun changingTheSourceTouchesOnlyIndexZeroAndRefusesAnythingBeyondFour() {
        val out = TideSettingCodec.withSource(unit2, 2)!!
        assertArrayEquals(bytes(0x2E, 2, 2, 0x34, 0x12, 0x78, 0x56, 0x01, 0xAB), out)
        assertNull(TideSettingCodec.withSource(unit2, 5))
        assertNull(TideSettingCodec.withSource(unit2, -1))
    }

    @Test
    fun theAppPortIdIsLowByteFirstAtIndexThreeAndFour() {
        assertArrayEquals(bytes(0x2E, 2, 4, 0x34, 0x12, 0x61, 0x09, 0x01, 0xAB), TideSettingCodec.withAppPort(unit2, 2401)!!)
        assertNull(TideSettingCodec.withAppPort(unit2, 70000))
    }

    @Test
    fun feetIsBitZeroOfIndexFiveAndTheOtherBitsStay() {
        assertArrayEquals(bytes(0x2E, 2, 4, 0x34, 0x12, 0x78, 0x56, 0x00, 0xAB), TideSettingCodec.withFeet(unit2, false)!!)
        val metres = bytes(0x2E, 2, 0, 0, 0, 0, 0, 0xF0)
        assertArrayEquals(bytes(0x2E, 2, 0, 0, 0, 0, 0, 0xF1), TideSettingCodec.withFeet(metres, true)!!)
    }

    private val point = TideUserPointCodec.Point(13, 45, 51.23, -2.5, 5.5f, true)

    @Test
    fun anOwnPointIsEncodedAsTheNotesSay() {
        // 13:45, lat 5123 = 0x1403 and lon -250 = 0xFFFFFF06 (high byte first), 5.5f = 40 B0 00 00 (high byte first), summer bit.
        assertArrayEquals(
            bytes(13, 45, 0, 0, 0x14, 0x03, 0xFF, 0xFF, 0xFF, 0x06, 0x40, 0xB0, 0, 0, 1),
            TideUserPointCodec.encode(point)!!,
        )
    }

    @Test
    fun theOffsetFloatIsBigEndianAndNegativeValuesWork() {
        val v = TideUserPointCodec.encode(point.copy(utcOffsetHours = -1f, summerTime = false))!!
        assertArrayEquals(bytes(0xBF, 0x80, 0, 0), v.copyOfRange(10, 14))
        assertEquals(0, v[14].toInt())
    }

    @Test
    fun anOwnPointSurvivesAWriteAndARead() {
        val frame = bytes(0x2E, 4) + TideUserPointCodec.encode(point)!!
        assertEquals(point, TideUserPointCodec.parse(frame, 2))
        assertNull(TideUserPointCodec.parse(frame, 1))
    }

    @Test
    fun aPointIsRefusedWhenAFieldIsOutOfRange() {
        assertNull(TideUserPointCodec.encode(point.copy(hour = 24)))
        assertNull(TideUserPointCodec.encode(point.copy(minute = 60)))
        assertNull(TideUserPointCodec.encode(point.copy(latitude = 90.5)))
        assertNull(TideUserPointCodec.encode(point.copy(longitude = -181.0)))
        assertNull(TideUserPointCodec.encode(point.copy(utcOffsetHours = 15f)))
        assertNull(TideUserPointCodec.encode(point.copy(utcOffsetHours = Float.NaN)))
    }

    @Test
    fun writingAPointChangesOnlyItsFifteenBytes() {
        val old = bytes(0x2E, 3) + ByteArray(15) + bytes(0x77)
        val out = TideUserPointCodec.withPoint(old, 1, point)!!
        assertEquals(old.size, out.size)
        assertEquals(0x77, out.last().toInt())
        assertArrayEquals(TideUserPointCodec.encode(point)!!, out.copyOfRange(2, 17))
        assertNull(TideUserPointCodec.withPoint(old, 2, point))
        assertNull(TideUserPointCodec.withPoint(bytes(0x2E, 3, 1, 2), 1, point))
    }

    @Test
    fun theUnitNumbersAreThreeToFive() {
        assertArrayEquals(bytes(0x2E, 3), TideUserPointCodec.request(1))
        assertArrayEquals(bytes(0x2E, 5), TideUserPointCodec.request(3))
        assertNull(TideUserPointCodec.request(0))
        assertNull(TideUserPointCodec.request(4))
    }

    @Test
    fun theTextFormIsParsedStrictly() {
        assertEquals(point, TidePointText.parse("13:45", "51,23", "-2.5", "5.5", true))
        assertNull(TidePointText.parse("1345", "51.23", "-2.5", "5.5", true))
        assertNull(TidePointText.parse("13:45", "abc", "-2.5", "5.5", true))
        assertNull(TidePointText.parse("25:00", "51.23", "-2.5", "5.5", true))
        assertNull(TidePointText.parse("13:45", "51.23", "-2.5", "NaN", true))
    }

    @Test
    fun unknownSourcesAreNamedNotHidden() {
        assertTrue(TideSettingCodec.sourceName(9).contains("9"))
        assertNotNull(TideSettingCodec.request())
        assertFalse(TideSettingCodec.parse(unit2)!!.let { it.source == 0 })
    }
}
