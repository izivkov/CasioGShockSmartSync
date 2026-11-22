package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId

/**
 * Guards the chunk rule: a block is sent as chunks of 512 data bytes, each followed by a 2-byte
 * checksum, and a chunk spans several notifications. The recorded session is upstream's 28 October
 * transfer (see the resource file for source and licence).
 */
class H5600LifeLogReassemblyTest {
    private class Recording(
        val notifications: List<ByteArray>,
        val announced: LinkedHashMap<Int, Int>,
    )

    private fun recorded(): Recording {
        val text = javaClass.getResourceAsStream("/h5600_lifelog_2026-10-28.txt")!!.bufferedReader().readLines()
        val notifications = mutableListOf<ByteArray>()
        val announced = LinkedHashMap<Int, Int>()
        for (line in text) {
            val part = line.trim().split(" ")
            when (part[0]) {
                "A" -> announced[part[1].toInt(16)] = part[2].toInt()
                "N" -> notifications += part[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }
        return Recording(notifications, announced)
    }

    @Test
    fun everyBlockOfTheRecordedSessionHasExactlyItsAnnouncedLength() {
        val rec = recorded()
        val stream = H5600HealthDecoder.lifeLogStream(rec.notifications, rec.announced)

        assertEquals(rec.announced.values.sum(), stream.size)
        // Walk the blocks: the header is 20 bytes, every later block starts with its tag and its length.
        var offset = rec.announced.getValue(0)
        for ((tag, length) in rec.announced.entries.drop(1)) {
            assertEquals("tag at $offset", tag, stream[offset].toInt() and 0xFF)
            val announcedInFrame = ByteBuffer.wrap(stream, offset + 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
            assertEquals("length of frame 0x${tag.toString(16)}", length, announcedInFrame)
            offset += length
        }
        assertEquals(stream.size, offset)
    }

    @Test
    fun theRecordedSessionDecodesWithAReadableHeader() {
        val rec = recorded()
        val data = H5600HealthDecoder.decode(rec.notifications, rec.announced, ZoneId.of("UTC"))

        assertNotNull(data)
        assertEquals(5353, data!!.currentDaySteps)
        assertEquals(1156, data.h5600?.energyKcal)
        assertTrue(data.warnings.none { it.contains("not reported") })
    }

    @Test
    fun aBlockOfSeveralChunksSpanningNotificationsKeepsEveryByte() {
        val header = ByteArray(20) { it.toByte() }
        val block = ByteArray(1200) { (it % 251).toByte() }
        fun encode(bytes: ByteArray): List<Byte> =
            bytes.toList().chunked(512).flatMap { it + listOf<Byte>(0x7E, 0x7E) }.map { (it.toInt() xor 0xFF).toByte() }
        val notifications = (encode(header) + encode(block)).chunked(18).map { byteArrayOf(0x05) + it.toByteArray() }

        val stream = H5600HealthDecoder.lifeLogStream(notifications, linkedMapOf(0 to 20, 0x50 to 1200))

        assertTrue((header + block).contentEquals(stream))
    }

    @Test
    fun aTruncatedTransferGivesWhatArrivedAndDoesNotCrash() {
        val rec = recorded()
        val cut = rec.notifications.take(40)

        val stream = H5600HealthDecoder.lifeLogStream(cut, rec.announced)

        assertTrue(stream.isNotEmpty())
        assertTrue(stream.size < rec.announced.values.sum())
    }
}
