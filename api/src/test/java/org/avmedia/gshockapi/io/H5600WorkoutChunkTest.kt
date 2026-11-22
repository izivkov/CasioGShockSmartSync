package org.avmedia.gshockapi.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class H5600WorkoutChunkTest {
    private fun sample(n: Int) = ByteArray(n) { (it * 7 + 3).toByte() }

    @Test
    fun crcKermitCheckValue() {
        assertEquals(0x2189, WorkoutCodec.crc16Kermit("123456789".toByteArray()))
    }

    @Test
    fun goodChunksComeBackInvertedBack() {
        val data = sample(1300)
        val got = WorkoutChunkAssembler.decode(WorkoutTestData.chunked(data, 512), 1300, 512)
        assertArrayEquals(data, got)
    }

    @Test
    fun aFlippedBitFailsTheChunk() {
        val raw = WorkoutTestData.chunked(sample(1300), 512)
        raw[700] = (raw[700].toInt() xor 1).toByte()
        assertNull(WorkoutChunkAssembler.decode(raw, 1300, 512))
    }

    @Test
    fun aBadSectorIsDroppedAndAskedAgain() {
        val data = sample(256)
        val good = WorkoutTestData.chunked(data, 256)
        val bad = good.copyOf().also { it[5] = (it[5].toInt() xor 0x10).toByte() }
        val asm = WorkoutChunkAssembler(256, 256)
        asm.feed(bad)
        assertTrue(asm.corrupt)
        assertFalse(asm.endSector())
        asm.dropSector()
        asm.feed(good)
        assertTrue(asm.endSector())
        assertArrayEquals(data, asm.result())
    }

    @Test
    fun missingBytesGiveNoResult() {
        val raw = WorkoutTestData.chunked(sample(600), 512)
        assertNull(WorkoutChunkAssembler.decode(raw.copyOf(raw.size - 10), 600, 512))
    }

    @Test
    fun fetchedMarkClearsOnlyTheTransferredBits() {
        val m = WorkoutGroup.fetchedMark(setOf(0, 3, 9), noneLeft = false)
        assertEquals(15, m.size)
        assertEquals(0x07, m[0].toInt())
        assertEquals(0, m[1].toInt())
        assertEquals(0xF6, m[2].toInt() and 0xFF)
        assertEquals(0xFD, m[3].toInt() and 0xFF)
        for (i in 4 until 15) assertEquals(0xFF, m[i].toInt() and 0xFF)
        assertEquals(1, WorkoutGroup.fetchedMark(emptySet(), noneLeft = true)[1].toInt())
    }

    @Test
    fun groupHeaderListsUsedAndPendingSlots() {
        val g = ByteArray(1024) { 0xFF.toByte() }
        g[0] = 0xFA.toByte()          // slots 0 and 2 used
        g[1] = 0xFE.toByte()          // slot 8 used
        g[525] = 0xFE.toByte()        // slot 0 already transferred
        assertEquals(listOf(0, 2, 8), WorkoutGroup.usedSlots(g))
        assertEquals(listOf(2, 8), WorkoutGroup.pendingSlots(g))
    }
}
