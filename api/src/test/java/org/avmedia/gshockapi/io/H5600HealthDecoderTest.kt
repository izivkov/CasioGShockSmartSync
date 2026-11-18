package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Builds a LifeLog stream by hand, so the decoder is checked without a watch or a capture.
 * Expected values are worked out from the layout, not copied from the decoder.
 */
class H5600HealthDecoderTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())

    private fun le32(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun record(
        value: Int,
        flag: Int = 0x08F0,
    ) = le16(value) + le16(flag)

    private fun frame(
        tag: Int,
        records: List<ByteArray>,
    ): ByteArray {
        val body = records.fold(ByteArray(0)) { acc, next -> acc + next }
        return byteArrayOf(tag.toByte()) + le32(body.size + 5) + body
    }

    /** 2026-10-26 22:18 UTC, the time of the 26 October 2026 capture. */
    private fun header(
        steps: Int,
        energy: Int,
        stepsTail: ByteArray = byteArrayOf(0, 0),
    ): ByteArray =
        byteArrayOf(0x00, 0x0F, 0x00, 0x00, 0x00, 26, 10, 26, 22, 18) +
            le32(0) + le16(steps) + stepsTail + le16(energy)

    /** Bulk notifications: 0x05 prefix, payload XOR 0xFF, 2-byte checksum. */
    private fun notifications(plain: ByteArray): List<ByteArray> {
        val encoded = plain.map { (it.toInt() xor 0xFF).toByte() }.toByteArray()
        return listOf(byteArrayOf(0x05) + encoded + byteArrayOf(0x00, 0x00))
    }

    @Test
    fun decodesTimeStepsEnergyAndSlotsIntoLocalHours() {
        val steps = frame(0x50, listOf(record(10), record(20), record(30)))
        val energy = frame(0x51, listOf(record(100, 0), record(200, 0), record(300, 0)))
        val plain = header(steps = 624, energy = 1263) + steps + energy
        val announced = mapOf(0x50 to 17, 0x51 to 17)

        val data = H5600HealthDecoder.decode(notifications(plain), announced, zone)

        assertNotNull(data)
        // 22:18 UTC is 18:18 in Toronto (EDT, before the 1 November change)
        assertEquals(LocalDateTime.of(2026, 10, 26, 18, 18), data!!.timestamp)
        assertEquals(1, data.dayOfWeek) // Monday, ISO numbering as in the ABL-100 model
        assertEquals(624, data.currentDaySteps)
        // three slots at 18:18, 18:13 and 18:08 local, all in hour 18
        assertEquals(60, data.hourlyByHour[18])
        assertEquals(3, data.hourlyIntervals.size)
        assertEquals(1263, data.h5600?.energyKcal)
        assertTrue(data.h5600?.energyIsProvisional == true)
    }

    @Test
    fun doesNotReportStepsWhenTheHeaderLayoutIsNotRecognised() {
        val steps = frame(0x50, listOf(record(10)))
        val energy = frame(0x51, listOf(record(100, 0)))
        val plain = header(steps = 624, energy = 1263, stepsTail = byteArrayOf(0x84.toByte(), 0x04)) + steps + energy

        val data = H5600HealthDecoder.decode(notifications(plain), mapOf(0x50 to 17, 0x51 to 17), zone)

        assertNotNull(data)
        assertNull(data!!.currentDaySteps)
        assertTrue(data.warnings.any { it.contains("not reported") })
    }

    @Test
    fun returnsNullWhenTheStreamHasNoLifeLogHeader() {
        val notStream = notifications(ByteArray(40) { 0x11 })

        assertNull(H5600HealthDecoder.decode(notStream, emptyMap(), zone))
    }

    @Test
    fun readsWatchConditionFlagsForLifeLogAndActivityLog() {
        val plain = header(steps = 0, energy = 0)
        val condition = byteArrayOf(0x28, 0x06, 0x1A, 0, 0, 0xFA.toByte(), 0, 0, 5, 1)

        val data = H5600HealthDecoder.decode(notifications(plain), emptyMap(), zone, watchCondition = condition)

        assertEquals(true, data?.h5600?.lifeLogPending)
        assertEquals(true, data?.h5600?.activityLogPending)
    }
}
