package org.avmedia.gshockapi.io

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heart rate per 2-minute slot (section 0x52) and the cardio group of the current values (0x65), from streams
 * built by hand. Expected values are worked out from the byte layout by hand, not from the decoder.
 */
class H5600DayCurveDecodeTest {
    private val zone = ZoneId.of("Europe/Brussels")

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun f32(v: Float) = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(v).array()

    private fun frame(tag: Int, body: ByteArray) = byteArrayOf(tag.toByte()) + le32(body.size + 5) + body

    /** 2026-11-23 08:09 UTC (09:09 in Brussels): an odd minute, so the first slot starts at 08:08. */
    private fun header() = byteArrayOf(0x00, 0x0F, 0x00, 0x00, 0x00, 26, 11, 23, 8, 9) + le32(0) + le16(100) + byteArrayOf(0, 0) + le16(50)

    private fun notifications(vararg blocks: ByteArray): List<ByteArray> =
        blocks.flatMap { block ->
            block.toList().chunked(512).flatMap { chunk -> chunk.map { (it.toInt() xor 0xFF).toByte() } + listOf<Byte>(0, 0) }
        }.chunked(18).map { byteArrayOf(0x05) + it.toByteArray() }

    private fun hr(vararg bpm: Int) = bpm.fold(ByteArray(0)) { acc, b -> acc + byteArrayOf(b.toByte(), 0x19, 0x00, 0x08) }

    @Test
    fun heartRateRecordsAreTwoMinuteSlotsNewestFirstAndComeOutOldestFirst() {
        val f = frame(0x52, hr(70, 0xFE, 80, 90))
        val data = H5600HealthDecoder.decode(notifications(header(), f), mapOf(0x52 to f.size), zone)!!
        val slots = data.h5600!!.heartRateSlots
        // records 0..3 start at 09:08, 09:06, 09:04, 09:02 local; the no-data record (09:06) is left out
        assertEquals(listOf(LocalDateTime.of(2026, 11, 23, 9, 2), LocalDateTime.of(2026, 11, 23, 9, 4), LocalDateTime.of(2026, 11, 23, 9, 8)), slots.map { it.time })
        assertEquals(listOf(90, 80, 70), slots.map { it.bpm })
    }

    @Test
    fun aTransferWithoutHeartRateHasNoSlots() {
        val data = H5600HealthDecoder.decode(notifications(header()), emptyMap(), zone)!!
        assertTrue(data.h5600!!.heartRateSlots.isEmpty())
    }

    private fun currentValues(cardio: ByteArray): ByteArray =
        byteArrayOf(0x5B) + cardio + byteArrayOf(0x60) + le16(3) + le16(2) + byteArrayOf(0x62) + ByteArray(21) +
            byteArrayOf(0x61) + ByteArray(49) { 0xFE.toByte() } + byteArrayOf(0x5E) + le16(150) + le16(50) + le16(58)

    @Test
    fun theCardioGroupOfTheCurrentValuesIsTodaysCardioLoad() {
        val body = currentValues(f32(1.25f) + byteArrayOf(2) + le16(340) + le16(410))
        val c = H5600CurrentValues.cardio(body, LocalDate.of(2026, 11, 23))!!
        assertEquals(1.25f, c.loadRatio!!, 1e-6f)
        assertEquals(2, c.feedback)
        assertEquals(340, c.strain)
        assertEquals(410, c.tolerance)
        assertEquals(LocalDate.of(2026, 11, 23), c.date)
        // the walk lands the heart rate group where the decoder reads the day's heart rate (body offset 88)
        assertEquals(88, H5600CurrentValues.groups(body)[0x5E])
    }

    @Test
    fun aCardioGroupWithoutValuesOrWithoutTheGroupIsNull() {
        val none = f32(Float.fromBits(0xFFFFFFFE.toInt())) + byteArrayOf(0xFE.toByte()) + le16(0xFFFE) + le16(0xFFFE)
        assertNull(H5600CurrentValues.cardio(currentValues(none), LocalDate.of(2026, 11, 23)))
        assertNull(H5600CurrentValues.cardio(byteArrayOf(0x60, 0, 0, 0, 0), LocalDate.of(2026, 11, 23)))
        assertNull(H5600CurrentValues.cardio(null, LocalDate.of(2026, 11, 23)))
        assertNull(H5600CurrentValues.cardio(byteArrayOf(0x5B, 1, 2), LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun theDecoderPutsTodaysCardioLoadIntoTheBodyReadings() {
        val cv = frame(0x65, currentValues(f32(0.8f) + byteArrayOf(1) + le16(100) + le16(200)))
        val data = H5600HealthDecoder.decode(notifications(header(), cv), mapOf(0x65 to cv.size), zone)!!
        val now = data.h5600!!.body!!.cardioNow!!
        assertEquals(0.8f, now.loadRatio!!, 1e-6f)
        assertEquals(LocalDate.of(2026, 11, 23), now.date)
        assertEquals(150, data.h5600!!.heartRate!!.maxBpm)
    }

    @Test
    fun aReadAsksForTheWorkoutsWhenFlaggedOrOnceAnHour() {
        val hour = WorkoutReadTrigger.CHECK_EVERY_MS
        assertTrue(WorkoutReadTrigger.shouldDownload(null, null, 1_000))
        assertTrue(WorkoutReadTrigger.shouldDownload(1, 1_000, 1_001))
        assertTrue(WorkoutReadTrigger.shouldDownload(2, 1_000, 1_001))
        assertEquals(false, WorkoutReadTrigger.shouldDownload(0, 1_000, 1_000 + hour - 1))
        assertEquals(false, WorkoutReadTrigger.shouldDownload(null, 1_000, 1_000 + hour - 1))
        assertTrue(WorkoutReadTrigger.shouldDownload(0, 1_000, 1_000 + hour))
        assertTrue(WorkoutReadTrigger.shouldDownload(0, 5_000, 1_000))
    }
}
