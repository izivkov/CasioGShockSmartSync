package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A watch that plays the frames of one real workout read (resource `h5600_workout_1f_read.txt`): session, group header,
 * both headers and the first data read, 4096 bytes in one sector. The watch sends the sector end `07 1F 00 00 00 00 00`
 * and then says nothing more, as the real one (log 2026-11-24 02:37): that sector end with every byte in is the end of the
 * block read, no `09` follows. It answers only a next request (`00 1F` or `00 20`), here with an abort.
 */
private class DataReadReplayWatch(private val inbox: Channel<WorkoutMsg>) : WorkoutLink {
    private sealed class Step(val channel: H5600Channel, val data: ByteArray)
    private class Tx(channel: H5600Channel, data: ByteArray) : Step(channel, data)
    private class Rx(channel: H5600Channel, data: ByteArray) : Step(channel, data)

    private val script: List<Step> =
        javaClass.getResourceAsStream("/h5600_workout_1f_read.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (dir, ch, hex) = line.trim().split(" ")
                val channel = if (ch == "BULK") H5600Channel.CONVOY else H5600Channel.DRSP
                val data = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (dir == "TX") Tx(channel, data) else Rx(channel, data)
            }

    /** The sector end the watch sent last; its whole echo is what lets the read go on. */
    private val sectorEnd: ByteArray = script.filterIsInstance<Rx>().last { it.data[0].toInt() == 0x07 }.data
    private var cursor = 0
    val writes = mutableListOf<String>()
    val mismatches = mutableListOf<String>()
    val nextRequests = mutableListOf<String>()

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** An answer to a frame of the watch (`07`, `09`) matches the logged short form and the whole frame alike. */
    private fun matches(want: ByteArray, got: ByteArray): Boolean =
        want.contentEquals(got) ||
            (want.size == 2 && want[0].toInt() in listOf(0x07, 0x09) && got.size >= 2 && got[0] == want[0] && got[1] == want[1])

    private fun write(channel: H5600Channel, data: ByteArray) {
        writes += (if (channel == H5600Channel.CONVOY) "bulk " else "") + hex(data)
        if (data.size >= 2 && data[0].toInt() == 0x07 && data[1] == sectorEnd[1]) {
            cursor = script.size // silent from here on
            return
        }
        if (cursor == script.size) {
            if (data.size == 10 && data[0].toInt() == 0 && (data[1].toInt() == 0x1F || data[1].toInt() == 0x20)) {
                nextRequests += hex(data)
                inbox.trySend(WorkoutMsg(H5600Channel.DRSP, byteArrayOf(0x03, data[1])))
            }
            return
        }
        val next = script.drop(cursor).indexOfFirst { it is Tx }
        if (next < 0) return
        val at = cursor + next
        val want = script[at]
        if (want.channel != channel || !matches(want.data, data)) {
            mismatches += "expected ${hex(want.data)}, got ${hex(data)}"
            return
        }
        cursor = at + 1
        while (cursor < script.size && script[cursor] is Rx) {
            inbox.trySend(WorkoutMsg(script[cursor].channel, script[cursor].data.copyOf()))
            cursor++
        }
    }

    override fun drsp(data: ByteArray) = write(H5600Channel.DRSP, data)

    override fun bulk(data: ByteArray) = write(H5600Channel.CONVOY, data)
}

class H5600WorkoutDataReadReplayTest {
    @Test fun theSectorEndOfADataReadIsEchoedWholeAndTheReadEnds() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = DataReadReplayWatch(inbox)
        val lines = mutableListOf<String>()
        val result = WorkoutTransfer(watch, inbox, sink = { true }, log = { lines += it }).run()
        val why = "error ${result.error}, mismatches ${watch.mismatches}, writes ${watch.writes.takeLast(8)}"

        assertTrue("the recorded writes match: $why", watch.mismatches.isEmpty())
        val at = watch.writes.indexOf("001f0040010000100000")
        assertTrue("the data read went out: $why", at >= 0)
        assertEquals("whole sector end echo: $why", "071f0000000000", watch.writes.getOrNull(at + 1))
        assertEquals("then at once the next request (next block or the laps): $why", 1, watch.nextRequests.size)
        assertEquals("no other write in between: $why", watch.nextRequests[0], watch.writes.getOrNull(at + 2))
        assertTrue("no 09 or 04 for the data read: $why", watch.writes.none { it == "091f0000000000" || it == "041f" })
        assertTrue("the data block is read: $why", lines.any { it.contains("slot 0 1F 1 data blocks") })
        assertFalse("not stalled on the data read: $why", result.error.orEmpty().contains("data blocks could not be read"))
    }
}
