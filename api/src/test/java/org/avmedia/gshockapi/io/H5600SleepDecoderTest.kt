package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.LocalDateTime

/** Sleep sections on synthetic records built from the documented layouts; expected values are worked out by hand. */
class H5600SleepDecoderTest {
    private fun bcd(value: Int): Byte = (((value / 10) shl 4) or (value % 10)).toByte()

    /** A 55-byte nightly record for 2026-11-21 with every field set. */
    private fun fullNight(): ByteArray {
        val r = ByteArray(55) { 0xFE.toByte() }
        val b = ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN)
        r[0] = bcd(26); r[1] = bcd(11); r[2] = bcd(21)
        r[3] = (-16).toByte()
        r[5] = 3; r[6] = 4
        r[12] = 52; r[13] = 55
        b.putShort(14, 1100); b.putShort(16, 1150)
        b.putShort(18, 48); b.putShort(20, 50)
        b.putFloat(22, 14.5f); b.putFloat(26, 15.0f)
        r[30] = 82
        r[34] = 7; r[35] = 12
        r[40] = 18
        r[42] = 1; r[43] = 40
        r[44] = 1; r[45] = 20
        r[46] = 4; r[47] = 12
        r[48] = 23; r[49] = 10
        r[50] = 6; r[51] = 22
        return r
    }

    private fun stageRecord(): ByteArray {
        val r = ByteArray(2016)
        val b = ByteBuffer.wrap(r).order(ByteOrder.LITTLE_ENDIAN)
        r[0] = bcd(26); r[1] = bcd(11); r[2] = bcd(21); r[3] = bcd(23); r[4] = bcd(10); r[5] = bcd(0)
        val pairs = listOf(0 to -2, 600 to -5, 2400 to -6, 5400 to -3, 7200 to -2)
        for (i in 0 until 250) {
            val at = 8 + i * 8
            if (i < pairs.size) {
                b.putInt(at, pairs[i].first); b.putInt(at + 4, pairs[i].second)
            } else {
                b.putInt(at, -2); b.putInt(at + 4, 0x7FFFFFFE)
            }
        }
        return r
    }

    @Test
    fun readsEveryFieldOfTheNightlyRecord() {
        val night = H5600SleepDecoder.decode(null, fullNight()).night
        assertNotNull(night)
        night!!
        assertEquals(LocalDate.of(2026, 11, 21), night.date)
        assertEquals(-16, night.zoneQuarterHours)
        assertEquals(82, night.sleepScore)
        assertEquals(3, night.rechargeStatus)
        assertEquals(4, night.rechargeLevel)
        assertEquals(432, night.totalSleepMinutes)
        assertEquals(100, night.remMinutes)
        assertEquals(80, night.deepMinutes)
        assertEquals(252, night.lightMinutes)
        assertEquals(18, night.awakeMinutes)
        assertEquals(52, night.heartRateNight)
        assertEquals(55, night.heartRateBaseline)
        assertEquals(48, night.heartRateVariability)
        assertEquals(14.5f, night.breathingRate!!, 0.001f)
        assertEquals(15.0f, night.breathingBaseline!!, 0.001f)
        assertEquals(LocalDateTime.of(2026, 11, 21, 23, 10), night.fellAsleep)
    }

    @Test
    fun aWakeTimeBeforeTheSleepTimeIsTheNextDay() {
        val night = H5600SleepDecoder.decode(null, fullNight()).night!!
        assertEquals(LocalDateTime.of(2026, 11, 22, 6, 22), night.wokeUp)
    }

    @Test
    fun aWakeTimeAfterTheSleepTimeStaysOnTheDate() {
        val r = fullNight()
        r[48] = 1; r[49] = 0
        val night = H5600SleepDecoder.decode(null, r).night!!
        assertEquals(LocalDateTime.of(2026, 11, 21, 6, 22), night.wokeUp)
    }

    @Test
    fun noDataMarksBecomeNullNotZero() {
        val r = fullNight()
        for (at in 5..54) r[at] = 0xFE.toByte()
        val data = H5600SleepDecoder.decode(null, r)
        assertNull(data.night)
        assertEquals(1, data.nights.size)
        assertTrue(data.nights[0].isEmpty)
        assertNull(data.nights[0].sleepScore)
    }

    @Test
    fun aScoreOutsideOneToHundredIsNoData() {
        val r = fullNight()
        r[30] = 0
        assertNull(H5600SleepDecoder.decode(null, r).night!!.sleepScore)
        r[30] = 101
        assertNull(H5600SleepDecoder.decode(null, r).night!!.sleepScore)
    }

    @Test
    fun theNewestNightWithDataWinsWhateverTheOrder() {
        val older = fullNight()
        older[2] = bcd(20)
        val empty = fullNight()
        empty[2] = bcd(22)
        for (at in 5..54) empty[at] = 0xFE.toByte()
        val data = H5600SleepDecoder.decode(null, empty + fullNight() + older)
        assertEquals(LocalDate.of(2026, 11, 21), data.night!!.date)
        assertEquals(3, data.nights.size)
    }

    @Test
    fun readsTheStageTimelineAsSecondsFromItsStart() {
        val data = H5600SleepDecoder.decode(stageRecord(), null)
        val start = LocalDateTime.of(2026, 11, 21, 23, 10)
        assertEquals(start, data.stagesStart)
        assertEquals(
            listOf("awake" to start, "light" to start.plusSeconds(600), "deep" to start.plusSeconds(2400), "rem" to start.plusSeconds(5400), "awake" to start.plusSeconds(7200)),
            data.stages.map { it.stage to it.time },
        )
    }

    @Test
    fun anEmptyTimelineHasNoStages() {
        val empty = ByteArray(2016)
        for (i in 0..5) empty[i] = 0xFE.toByte()
        val data = H5600SleepDecoder.decode(empty, null)
        assertTrue(data.stages.isEmpty())
        assertNull(data.stagesStart)
    }

    @Test
    fun aTruncatedBodyIsIgnoredNotACrash() {
        val data = H5600SleepDecoder.decode(ByteArray(100), ByteArray(30))
        assertNull(data.night)
        assertTrue(data.stages.isEmpty())
    }
}
