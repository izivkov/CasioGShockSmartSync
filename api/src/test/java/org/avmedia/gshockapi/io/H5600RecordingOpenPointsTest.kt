package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.ZoneId

/**
 * Settles open points of the LifeLog layout on the real recording of 28 October 2026 (see the resource file).
 * That day holds no night, so the sleep sections are checked for their no-data marks only.
 */
class H5600RecordingOpenPointsTest {
    private val announced = LinkedHashMap<Int, Int>()
    private val notifications = mutableListOf<ByteArray>()

    private fun load() {
        val text = javaClass.getResourceAsStream("/h5600_lifelog_2026-10-28.txt")!!.bufferedReader().readLines()
        for (line in text) {
            val part = line.trim().split(" ")
            when (part[0]) {
                "A" -> announced[part[1].toInt(16)] = part[2].toInt()
                "N" -> notifications += part[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }
    }

    /** The block of one tag, header included, cut out of the reassembled stream. */
    private fun block(
        stream: ByteArray,
        tag: Int,
    ): ByteArray {
        var offset = 0
        for ((t, length) in announced) {
            if (t == tag) return stream.copyOfRange(offset, offset + length)
            offset += length
        }
        error("no block $tag")
    }

    @Test
    fun theU32AtOffset10OfTheHeaderIsTheTransferSizeMinus14NotADistance() {
        load()
        val stream = H5600HealthDecoder.lifeLogStream(notifications, announced)
        val value = ByteBuffer.wrap(stream, 10, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(announced.values.sum(), value + 14)
    }

    @Test
    fun theHeartRatePeriodIsTwoMinutes() {
        load()
        // The heart-rate section and the 5-minute step section cover the same time (the whole log of the
        // watch). 494 heart-rate records at 2 minutes and 198 step records at 5 minutes are 988 and 990 minutes;
        // at 1 or at 5 minutes the two would be 2.5 or 5 times apart.
        val heartMinutes = (announced.getValue(0x52) - 5) / 4 * 2
        val stepMinutes = (announced.getValue(0x50) - 5) / 4 * 5
        assertTrue("$heartMinutes vs $stepMinutes", Math.abs(heartMinutes - stepMinutes) <= 5)
        val activityMinutes = (announced.getValue(0x56) - 5) / 3
        assertTrue("$activityMinutes vs $stepMinutes", Math.abs(activityMinutes - stepMinutes) <= 5)
    }

    @Test
    fun theZoneByteIsInQuarterHoursAndMatchesTheRecordedZone() {
        load()
        val stream = H5600HealthDecoder.lifeLogStream(notifications, announced)
        val night = block(stream, 0x5A)
        val zoneByte = night[5 + 3].toInt()
        assertEquals(-16, zoneByte)
        // the recording was made in Toronto, UTC-4 on 28 October 2026
        val offset = ZoneId.of("America/Toronto").rules.getOffset(LocalDate.of(2026, 10, 28).atStartOfDay())
        assertEquals(offset.totalSeconds, zoneByte * 15 * 60)
        assertEquals(zoneByte, block(stream, 0x53)[5 + 6].toInt())
    }

    @Test
    fun theRecordedDayHasNoNightAndEveryHealthSectionSaysNoData() {
        load()
        val data = H5600HealthDecoder.decode(notifications, announced, ZoneId.of("UTC"))
        assertNotNull(data)
        val health = data!!.h5600!!
        assertNull(health.sleep!!.night)
        assertEquals(1, health.sleep!!.nights.size)
        assertEquals(LocalDate.of(2026, 10, 28), health.sleep!!.nights[0].date)
        assertTrue(health.sleep!!.stages.isEmpty())
        assertNull(health.body!!.cardio)
        assertTrue(health.body!!.spo2.isEmpty())
    }

    @Test
    fun theRecordedHeaderStillReadsTheSameSteps() {
        load()
        val data = H5600HealthDecoder.decode(notifications, announced, ZoneId.of("UTC"))
        assertEquals(5353, data!!.currentDaySteps)
    }
}
