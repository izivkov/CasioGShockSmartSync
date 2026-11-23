package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scripted watch holding one running workout in slot 0 (header at 4096, one data block at 8192, one lap block at 12288).
 * It echoes the session request `00 1C` + 8 zero as the real watch does (owner dump 2026-11-23i: `001c0000000000000000`).
 */
internal class FakeWatch(private val inbox: Channel<WorkoutMsg>, private val corruptFirstHeader: Boolean = false) : WorkoutLink {
    val events = mutableListOf<String>()
    val marks = mutableListOf<ByteArray>()
    private var corrupted = false
    private val store = HashMap<Pair<Int, Long>, ByteArray>()

    init {
        val samples = (0 until 3).fold(ByteArray(0)) { acc, i -> acc + WorkoutTestData.runningSample(i, 120 + i) }
        val lap = WorkoutTestData.runningLap(0)
        val group = ByteArray(1024) { 0xFF.toByte() }
        group[0] = 0xFE.toByte()
        for (i in 0 until 4) store[0x1D to i * 256L] = group.copyOfRange(i * 256, i * 256 + 256)
        store[0x1E to 4096L] = WorkoutTestData.header(dataBytes = samples.size.toLong(), lapBytes = lap.size.toLong(), dataAddr = 8192, lapAddr = 12288)
        store[0x1F to 8192L] = WorkoutTestData.block(samples)
        store[0x20 to 12288L] = WorkoutTestData.block(lap)
    }

    private fun send(channel: H5600Channel, vararg b: Int) = inbox.trySend(WorkoutMsg(channel, ByteArray(b.size) { b[it].toByte() }))

    override fun bulk(data: ByteArray) {
        when (data[0].toInt()) {
            0 -> send(H5600Channel.CONVOY, 0, 0, 0)
            4 -> send(H5600Channel.CONVOY, 4, 0, 0)
            6 -> if (data.size == 14 && data[1].toInt() == 0) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(6, 0, 2, 0, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0))) else send(H5600Channel.CONVOY, 6, 1)
            7 -> { marks += data; events += "mark"; inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(7, 0))) }
        }
    }

    override fun drsp(data: ByteArray) {
        if (data.size != 10 || data[0].toInt() != 0) { events += "drsp ${data[0]}"; return }
        val cat = data[1].toInt()
        val addr = (0 until 4).sumOf { (data[2 + it].toLong() and 0xFF) shl (8 * it) }
        val len = (0 until 4).sumOf { (data[6 + it].toInt() and 0xFF) shl (8 * it) }
        if (cat == 0x1C) { inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, data.copyOf())); return }
        events += "read $cat $addr"
        var raw = WorkoutTestData.chunked(store.getValue(cat to addr), if (len == 256) 256 else 512)
        if (corruptFirstHeader && cat == 0x1D && addr == 0L && !corrupted) {
            corrupted = true
            raw = raw.copyOf().also { it[3] = (it[3].toInt() xor 0x40).toByte() }
        }
        var pos = 0
        while (pos < raw.size) {
            val n = minOf(19, raw.size - pos)
            inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(5) + raw.copyOfRange(pos, pos + n)))
            pos += n
        }
        send(H5600Channel.DRSP, 0x07)
        send(H5600Channel.DRSP, 0x09)
    }
}

class H5600WorkoutTransferTest {
    @Test
    fun oneWorkoutIsStoredThenMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val got = mutableListOf<org.avmedia.gshockapi.model.WorkoutSession>()
        val result = WorkoutTransfer(watch, inbox, sink = { got += it; watch.events += "stored"; true }).run()

        assertNull(result.error)
        assertEquals(1, result.downloaded)
        assertEquals(1, got.size)
        assertEquals(3, got[0].samples.size)
        assertEquals(1, got[0].laps.size)
        assertEquals(listOf("stored", "mark"), watch.events.filter { it == "stored" || it == "mark" })
        assertEquals(1, watch.marks.size)
        assertEquals(0x07, watch.marks[0][0].toInt())
        assertEquals(1, watch.marks[0][1].toInt())
        assertEquals(0xFE, watch.marks[0][2].toInt() and 0xFF)
    }

    /** Owner log 2026-11-23h: a workout read left no line at all. Every category and the stored mark are logged now. */
    @Test
    fun eachCategoryAndSlotIsLogged() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(FakeWatch(inbox), inbox, sink = { true }, log = { lines += it }).run()
        assertNull(result.error)
        for (want in listOf("1C requested", "1C the watch is ready", "1D group header read, 1 on the watch, 1 new (slots [0])", "1E 1 workout headers",
            "slot 0 1F 1 data blocks", "slot 0 20 1 lap blocks", "slot 0 stored and marked")) {
            assertTrue("$want in $lines", lines.any { it.contains(want) })
        }
    }

    @Test
    fun aWorkoutTheSinkRefusesIsNeverMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val result = WorkoutTransfer(watch, inbox, sink = { false }).run()
        assertNotNull(result.error)
        assertEquals(0, result.downloaded)
        assertTrue(watch.marks.isEmpty())
    }

    @Test
    fun aSinkThatThrowsIsNeverMarked() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox)
        val result = WorkoutTransfer(watch, inbox, sink = { error("disk full") }).run()
        assertNotNull(result.error)
        assertTrue(watch.marks.isEmpty())
    }

    @Test
    fun aBadChunkIsReadAgainAndTheTransferGoesOn() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = FakeWatch(inbox, corruptFirstHeader = true)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertNull(result.error)
        assertEquals(1, result.downloaded)
        assertEquals(2, watch.events.count { it == "read 29 0" })
    }

    /** Answers only the start message, with the given state and reason; records what the transfer writes. */
    private class StartOnlyWatch(private val inbox: Channel<WorkoutMsg>, private val state: Int, private val reason: Int) : WorkoutLink {
        val bulks = mutableListOf<ByteArray>()

        override fun drsp(data: ByteArray) {
            if (data.size == 10 && data[1].toInt() == 0x1C) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, data.copyOf()))
        }

        override fun bulk(data: ByteArray) {
            bulks += data
            if (data[0].toInt() == 0) inbox.trySend(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(0, state.toByte(), reason.toByte())))
        }
    }

    // Notes I5 says start state 0 means "ok, go on" on the tide path; notes H1 step 2 says 0 means "cannot". Our code treats 0 as go
    // on (the one real watch run worked that way); this pins it, and pins that a non-zero state with reason 1 or 2 is refused.
    @Test
    fun startStateZeroGoesOnToTheConnectionParameters() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = StartOnlyWatch(inbox, state = 0, reason = 0)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertTrue(watch.bulks.any { it[0].toInt() == 4 })
        assertTrue(result.error!!.contains("connection parameters"))
        assertTrue(!result.refused)
    }

    @Test
    fun aNonZeroStartStateWithReasonOneIsARefusalForTheBattery() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = StartOnlyWatch(inbox, state = 1, reason = 1)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertTrue(result.refused)
        assertTrue(result.error!!.contains("battery"))
    }
}
