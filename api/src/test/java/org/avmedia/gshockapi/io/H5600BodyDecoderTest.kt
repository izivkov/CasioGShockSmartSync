package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.LocalDateTime

/** SpO2, breathing, cardio status and stairs records on synthetic bytes built from the documented shapes. */
class H5600BodyDecoderTest {
    private fun bcd(value: Int): Byte = (((value / 10) shl 4) or (value % 10)).toByte()

    private fun stamp(r: ByteArray, second: Boolean) {
        r[0] = bcd(26); r[1] = bcd(11); r[2] = bcd(21); r[3] = bcd(7); r[4] = bcd(45)
        if (second) r[5] = bcd(30)
    }

    @Test
    fun readsASpo2Reading() {
        val r = ByteArray(10) { 0 }
        stamp(r, false)
        r[6] = 97
        val body = H5600BodyDecoder.decode(r, null, null, null)
        assertEquals(1, body.spo2.size)
        assertEquals(97, body.spo2[0].percent)
        assertEquals(LocalDateTime.of(2026, 11, 21, 7, 45), body.spo2[0].time)
    }

    @Test
    fun aSpo2ReadingWithoutValueIsDropped() {
        val r = ByteArray(10) { 0 }
        stamp(r, false)
        r[6] = 0xFE.toByte()
        assertTrue(H5600BodyDecoder.decode(r, null, null, null).spo2.isEmpty())
    }

    @Test
    fun readsACardioRecord() {
        val r = ByteArray(14) { 0xFE.toByte() }
        r[0] = bcd(26); r[1] = bcd(11); r[2] = bcd(21)
        val b = ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN)
        b.putFloat(4, 1.25f)
        r[8] = 2
        b.putShort(9, 310); b.putShort(11, 420)
        val cardio = H5600BodyDecoder.decode(null, null, r, null).cardio!!
        assertEquals(LocalDate.of(2026, 11, 21), cardio.date)
        assertEquals(1.25f, cardio.loadRatio!!, 0.001f)
        assertEquals(2, cardio.feedback)
        assertEquals(310, cardio.strain)
        assertEquals(420, cardio.tolerance)
    }

    @Test
    fun aCardioRecordOfNoDataMarksIsNull() {
        val r = ByteArray(14) { 0xFE.toByte() }
        r[0] = bcd(26); r[1] = bcd(11); r[2] = bcd(21)
        assertNull(H5600BodyDecoder.decode(null, null, r, null).cardio)
    }

    @Test
    fun readsAStairsRecordAsUpAndDown() {
        val r = ByteArray(12) { 0 }
        stamp(r, true)
        val b = ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(8, 12); b.putShort(10, 9)
        val stairs = H5600BodyDecoder.decode(null, null, null, r).stairs
        assertEquals(1, stairs.size)
        assertEquals(12, stairs[0].up)
        assertEquals(9, stairs[0].down)
        assertEquals(LocalDateTime.of(2026, 11, 21, 7, 45, 30), stairs[0].time)
    }

    @Test
    fun readsABreathingSessionTime() {
        val r = ByteArray(15) { 0 }
        stamp(r, false)
        r[6] = bcd(1); r[7] = bcd(30)
        val sessions = H5600BodyDecoder.decode(null, r, null, null).breathingSessions
        assertEquals(1, sessions.size)
        assertEquals(LocalDateTime.of(2026, 11, 21, 7, 45), sessions[0].time)
        assertEquals(listOf(1, 30), sessions[0].durationDigits.take(2))
    }

    @Test
    fun missingSectionsGiveEmptyLists() {
        val body = H5600BodyDecoder.decode(null, null, null, null)
        assertTrue(body.spo2.isEmpty() && body.stairs.isEmpty() && body.breathingSessions.isEmpty())
        assertNull(body.cardio)
    }
}
