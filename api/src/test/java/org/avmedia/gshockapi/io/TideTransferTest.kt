package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scripted watch for the port record transfer. */
private class TideWatch(
    private val inbox: Channel<WorkoutMsg>,
    private val state: Int = 0,
    private val size: Int = 0x00A0,
    private val answerStart: Boolean = true,
    private val endAnswer: Int? = 0x09,
    private val total: Int,
) : WorkoutLink {
    val drspWrites = mutableListOf<ByteArray>()
    val bulkWrites = mutableListOf<ByteArray>()

    private fun reply(vararg b: Int) = inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, ByteArray(b.size) { b[it].toByte() }))

    override fun drsp(data: ByteArray) {
        drspWrites += data
        if (data[0].toInt() == 0 && answerStart) {
            reply(0, state, if (state == 0) 0 else 1)
            if (state == 0) reply(2, size and 0xFF, size shr 8)
        }
    }

    override fun bulk(data: ByteArray) {
        bulkWrites += data
        val got = bulkWrites.sumOf { it.size - 1 }
        if (got >= total && endAnswer != null) inbox.trySend(WorkoutMsg(H5600Channel.DRSP, byteArrayOf(endAnswer.toByte(), 0x23)))
    }
}

class TideTransferTest {
    private val record = ByteArray(163) { (it * 7).toByte() }

    private fun run(
        state: Int = 0,
        size: Int = 0x00A0,
        answerStart: Boolean = true,
        endAnswer: Int? = 0x09,
    ): Triple<TideRunResult, TideWatch, Channel<WorkoutMsg>> {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = TideWatch(inbox, state, size, answerStart, endAnswer, record.size)
        var result: TideRunResult? = null
        runTest { result = TideTransfer(watch, inbox).run(record) }
        return Triple(result!!, watch, inbox)
    }

    @Test
    fun theStartRequestIsZeroZeroTwentyThreeAndTheLengthInThreeBytesLowFirst() {
        assertArrayEquals(byteArrayOf(0, 0x23, 0xA3.toByte(), 0, 0), TideFraming.start(163))
        assertArrayEquals(byteArrayOf(0, 0x23, 0xF1.toByte(), 3, 0), TideFraming.start(1009))
        assertArrayEquals(byteArrayOf(4, 0x23), TideFraming.end())
        assertArrayEquals(byteArrayOf(3, 0x23), TideFraming.abort())
    }

    @Test
    fun stateZeroIsGoOnAndAnyOtherStateIsNo() {
        assertEquals(0 to 0, TideFraming.parseStart(byteArrayOf(0, 0, 0)))
        assertEquals(2 to 1, TideFraming.parseStart(byteArrayOf(0, 2, 1)))
        assertNull(TideFraming.parseStart(byteArrayOf(2, 0, 0)))
    }

    @Test
    fun theChunkIsTheAnnouncedSizeMinusFourAndOnlyKindsTwoAndSixCount() {
        assertEquals(0x9C, TideFraming.parseChunkSize(byteArrayOf(2, 0xA0.toByte(), 0)))
        assertEquals(0x9C, TideFraming.parseChunkSize(byteArrayOf(6, 0xA0.toByte(), 0)))
        assertNull(TideFraming.parseChunkSize(byteArrayOf(4, 0xA0.toByte(), 0)))
        assertNull(TideFraming.parseChunkSize(byteArrayOf(2, 4, 0)))
        assertNull(TideFraming.parseChunkSize(byteArrayOf(2, 0, 0x10)))
    }

    @Test
    fun theRecordIsCutIntoPlainFiveChunksWithoutLosingAByte() {
        val chunks = TideFraming.chunks(record, 100)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it[0].toInt() == 5 })
        assertEquals(100, chunks[0].size - 1)
        assertEquals(63, chunks[1].size - 1)
        assertArrayEquals(record, chunks.fold(ByteArray(0)) { acc, c -> acc + c.copyOfRange(1, c.size) })
    }

    @Test
    fun aTransferStartsSendsEveryChunkAndEndsWithZeroFourTwentyThree() {
        val (result, watch) = run()
        assertNull(result.error)
        assertArrayEquals(TideFraming.start(163), watch.drspWrites.first())
        assertArrayEquals(TideFraming.end(), watch.drspWrites.last())
        assertEquals(2, watch.drspWrites.size)
        assertEquals(2, watch.bulkWrites.size)
        assertArrayEquals(record, watch.bulkWrites.fold(ByteArray(0)) { acc, c -> acc + c.copyOfRange(1, c.size) })
    }

    @Test
    fun aWatchThatRefusesTheStartGetsTheAbortAndNoData() {
        val (result, watch) = run(state = 1)
        assertNotNull(result.error)
        assertTrue(watch.bulkWrites.isEmpty())
        assertArrayEquals(TideFraming.abort(), watch.drspWrites.last())
    }

    @Test
    fun aWatchThatDoesNotAnswerTheStartGetsTheAbort() {
        val (result, watch) = run(answerStart = false)
        assertNotNull(result.error)
        assertTrue(watch.bulkWrites.isEmpty())
        assertArrayEquals(TideFraming.abort(), watch.drspWrites.last())
    }

    @Test
    fun anUnusableChunkSizeEndsInTheAbort() {
        val (result, watch) = run(size = 2)
        assertNotNull(result.error)
        assertTrue(watch.bulkWrites.isEmpty())
        assertArrayEquals(TideFraming.abort(), watch.drspWrites.last())
    }

    @Test
    fun noEndMessageFromTheWatchIsAnErrorAndTheAbort() {
        val (result, watch) = run(endAnswer = null)
        assertNotNull(result.error)
        assertArrayEquals(TideFraming.abort(), watch.drspWrites.last())
    }

    @Test
    fun anAbortFromTheWatchAtTheEndIsAnErrorAndNeverTheEndMessage() {
        val (result, watch) = run(endAnswer = 0x03)
        assertNotNull(result.error)
        assertTrue(watch.drspWrites.none { it[0].toInt() == 4 })
        assertArrayEquals(TideFraming.abort(), watch.drspWrites.last())
    }
}
