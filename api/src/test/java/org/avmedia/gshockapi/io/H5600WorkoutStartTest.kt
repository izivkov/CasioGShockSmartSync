package org.avmedia.gshockapi.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun frame(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/**
 * Steps 1 to 3 of the workout transfer with the timing of a real watch:
 * the start state `000000` comes 1.35 s after our write, the echo `001c0000000000000000` of the session request 6 to 10 s
 * after it, and the watch answers `04` only after that echo (`04000000000000000000`, 0.8 s later). Later steps go to [FakeWatch].
 */
private class ReplayWatch(
    private val inbox: Channel<WorkoutMsg>,
    private val scope: CoroutineScope,
    private val echoAfterMs: Long? = 7_600L,
    private val stateAfterMs: Long = 1_350L,
    private val stateFrame: ByteArray? = frame("000000"),
    private val stateChannel: H5600Channel = H5600Channel.CONVOY,
) : WorkoutLink {
    private val inner = FakeWatch(inbox)
    val drsps = mutableListOf<ByteArray>()
    val bulks = mutableListOf<ByteArray>()
    private var echoed = false
    private var waitingParams = 0

    private fun later(ms: Long, channel: H5600Channel, data: ByteArray) {
        scope.launch { delay(ms); inbox.send(WorkoutMsg(channel, data)) }
    }

    override fun drsp(data: ByteArray) {
        drsps += data
        if (data.size == 10 && data[0].toInt() == 0 && data[1].toInt() == 0x1C) {
            val echo = echoAfterMs ?: return
            scope.launch {
                delay(echo)
                inbox.send(WorkoutMsg(H5600Channel.CONVOY, data.copyOf()))
                echoed = true
                repeat(waitingParams) { later(800L * (it + 1), H5600Channel.CONVOY, frame("04000000000000000000")) }
                waitingParams = 0
            }
            return
        }
        inner.drsp(data)
    }

    override fun bulk(data: ByteArray) {
        bulks += data
        when (data[0].toInt()) {
            0 -> stateFrame?.let { later(stateAfterMs, stateChannel, it) }
            4 -> if (echoed) later(800L, H5600Channel.CONVOY, frame("04000000000000000000")) else waitingParams++
            else -> inner.bulk(data)
        }
    }
}

class H5600WorkoutStartTest {
    private fun TestScope.transfer(watch: WorkoutLink, inbox: Channel<WorkoutMsg>, lines: MutableList<String> = mutableListOf()) =
        WorkoutTransfer(watch, inbox, sink = { true }, log = { lines += "${testScheduler.currentTime} $it" })

    /** The echo came 7.6 s after the request, and the run stopped with "no answer to the connection parameters". */
    @Test
    fun aSlowSessionAnswerIsAwaitedBeforeTheConnectionParameters() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = ReplayWatch(inbox, backgroundScope)
        val lines = mutableListOf<String>()
        val result = transfer(watch, inbox, lines).run()
        assertNull("$lines", result.error)
        assertEquals(1, result.downloaded)
        // Each step logs its bytes: our writes and the watch's answers.
        for (want in listOf("00 1C 00 00 00 00 00 00 00 00", "00 00 00", "04 00 00 00 00 00 00 00 00 00", "04 01 18 00 18 00 00 00 DC 05", "06 00 02 00 10")) {
            assertTrue("$want in $lines", lines.any { it.contains(want) })
        }
    }

    /** The echo may come before the start state (other order): it is not a start state of 0x1C. */
    @Test
    fun theEchoBeforeTheStartStateIsNotReadAsAState() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = ReplayWatch(inbox, backgroundScope, echoAfterMs = 200L, stateAfterMs = 1_350L)
        val result = transfer(watch, inbox).run()
        assertNull(result.error)
        assertEquals(1, watch.bulks.count { it[0].toInt() == 0 })
    }

    /** The watch answered `03 01` (fail reason 1); we waited 5 s and then said "did not answer the start". */
    @Test
    fun aFailReasonEndsTheStartAtOnceWithTheAbort() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = ReplayWatch(inbox, backgroundScope, stateAfterMs = 240L, stateFrame = frame("0301"), stateChannel = H5600Channel.DRSP)
        val result = transfer(watch, inbox).run()
        assertTrue("${result.error}", result.error!!.contains("fail reason 1"))
        assertTrue(testScheduler.currentTime < 1_000L)
        assertTrue(watch.bulks.none { it[0].toInt() == 4 })
        assertEquals("031c", watch.drsps.last().joinToString("") { "%02x".format(it) })
    }

    /** A limit counts from our own write: other frames arriving in between do not stretch it. */
    @Test
    fun theStartStateLimitCountsFromOurWrite() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = ReplayWatch(inbox, backgroundScope, echoAfterMs = null, stateFrame = null)
        backgroundScope.launch { repeat(10) { delay(2_000L); inbox.send(WorkoutMsg(H5600Channel.CONVOY, frame("05FFFFFF"))) } }
        val result = transfer(watch, inbox).run()
        assertTrue("${result.error}", result.error!!.contains("start"))
        assertTrue("${testScheduler.currentTime}", testScheduler.currentTime <= 6_000L)
        assertEquals("031c", watch.drsps.last().joinToString("") { "%02x".format(it) })
    }

    /** A run that is cancelled after the session request still aborts the session, so the next attempt starts clean. */
    @Test
    fun aCancelledRunAbortsTheSessionAndTheNextRunWorks() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val first = ReplayWatch(inbox, backgroundScope, echoAfterMs = null)
        val job = launch { transfer(first, inbox).run() }
        delay(3_000L)
        job.cancel()
        job.join()
        assertEquals("031c", first.drsps.last().joinToString("") { "%02x".format(it) })

        val fresh = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val second = ReplayWatch(fresh, backgroundScope)
        val result = transfer(second, fresh).run()
        assertNull(result.error)
        assertEquals(1, result.downloaded)
    }
}
