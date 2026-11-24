package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.log.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The watch answers our `00 00 00` with `00 01 04` (busy, reason 4) while it finishes a time exchange, and repeats that frame
 * about every 60 ms: 130 frames in 8 s (here 30 per write). Our read counted each queued frame as a try and gave up after 5 tries in 8 s.
 * [busyWrites] writes of `00 00 00` get the flood, the next one `00 00 00` (ready); the rest is [FakeWatch].
 */
private class BusyFloodWatch(private val inbox: Channel<WorkoutMsg>, private val scope: CoroutineScope, private val busyWrites: Int) : WorkoutLink {
    private val inner = FakeWatch(inbox)
    var starts = 0

    override fun drsp(data: ByteArray) = inner.drsp(data)

    override fun bulk(data: ByteArray) {
        if (data.size == 3 && data.all { it.toInt() == 0 }) {
            if (++starts <= busyWrites) {
                scope.launch {
                    repeat(30) {
                        delay(60)
                        inbox.send(WorkoutMsg(H5600Channel.CONVOY, byteArrayOf(0, 1, 4)))
                    }
                }
                return
            }
        }
        inner.bulk(data)
    }
}

class WorkoutStartBusyTest {
    @Test fun aFloodOfBusyAnswersCountsOneTryPerWriteAndTheReadGoesOnWithinThirtySeconds() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = BusyFloodWatch(inbox, backgroundScope, busyWrites = 8)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertNull("busy for about 16 s, then ready: ${result.error}", result.error)
        assertEquals(1, result.downloaded)
        assertEquals("one start write per try", 9, watch.starts)
    }

    @Test fun aWatchBusyForMoreThanThirtySecondsEndsTheRead() = runTest {
        val inbox = Channel<WorkoutMsg>(Channel.UNLIMITED)
        val watch = BusyFloodWatch(inbox, backgroundScope, busyWrites = 100)
        val result = WorkoutTransfer(watch, inbox, sink = { true }).run()
        assertTrue(result.error.orEmpty(), result.error.orEmpty().contains("stayed busy"))
        assertTrue("about 30 s of tries: ${watch.starts}", watch.starts in 12..20)
    }
}

/**
 * A read while a settings or time exchange runs (0x1F city read, then `39 05`): the watch answered our start with `03 01`
 * (fail reason 1) at once. The read waits for the exchange to end, and a `03 01` at the start is tried once more.
 */
class WorkoutStartAfterExchangeTest {
    private val savedSink = H5600WorkoutIO.sink
    private val savedLink = H5600WorkoutIO.link
    private val forward = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = CopyOnWriteArrayList<String>()

    @Before fun install() {
        ConnectionSetup.reset()
        ConnectionSetup.onWatchSetupDone()
        H5600WorkoutIO.resetBackoff()
    }

    @After fun restore() {
        forward.cancel()
        H5600WorkoutIO.sink = savedSink
        H5600WorkoutIO.link = savedLink
        H5600WorkoutIO.resetBackoff()
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    /** [FakeWatch] delivered like the connection does; the first [refusals] start writes get `03 01`. */
    private fun install(refusals: Int): FakeWatch {
        val toApp = Channel<WorkoutMsg>(Channel.UNLIMITED)
        forward.launch {
            for (m in toApp) {
                val hex = m.data.joinToString(" ") { "%02X".format(it) }
                if (m.channel == H5600Channel.DRSP) H5600WorkoutIO.onDrsp(hex) else H5600WorkoutIO.onConvoy(hex)
            }
        }
        val inner = FakeWatch(toApp)
        var refused = 0
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) {
                writes += data.joinToString("") { "%02x".format(it) }
                inner.drsp(data)
            }

            override fun bulk(data: ByteArray) {
                writes += "bulk " + data.joinToString("") { "%02x".format(it) }
                if (data.size == 3 && data.all { it.toInt() == 0 } && refused < refusals) {
                    refused++
                    H5600WorkoutIO.onConvoy("03 01")
                    return
                }
                inner.bulk(data)
            }
        }
        return inner
    }

    @Test fun noWorkoutStartWhileASettingsExchangeRuns() = runBlocking {
        install(refusals = 0)
        H5600WorkoutIO.sink = { true }
        val exchangeEnds = CompletableDeferred<Unit>()
        val exchange = async(Dispatchers.IO) { WatchExchange.settingsExchange { exchangeEnds.await() } }
        delay(100)
        val read = async(Dispatchers.IO) { H5600WorkoutIO.download(userAsked = true) }
        delay(500)
        assertTrue("nothing sent while the exchange runs: $writes", writes.isEmpty())
        exchangeEnds.complete(Unit)
        exchange.await()
        val result = withTimeoutOrNull(15_000) { read.await() }
        assertNotNull(result)
        assertNull(result!!.error)
        assertEquals("001c0000000000000000", writes.first())
    }

    @Test fun aFailReasonAtTheStartIsTriedOnceMore() = runBlocking {
        install(refusals = 1)
        val stored = CopyOnWriteArrayList<Any>()
        H5600WorkoutIO.sink = { stored += it; true }
        val result = withTimeoutOrNull(20_000) { H5600WorkoutIO.download(userAsked = true) }
        assertNotNull(result)
        assertNull("${result!!.error} ${AppLog.recentEvents().takeLast(10)}", result.error)
        assertEquals(1, stored.size)
        assertEquals("two session requests", 2, writes.count { it == "001c0000000000000000" })
    }

    /**
     * A workout read 5 ms after "LifeLog read done", no settings read running: the watch answered `03 01` at once; a read with
     * a pause before it was accepted. The watch here refuses every start within 3 s of the end of the last transfer.
     */
    @Test fun aWorkoutReadRightAfterALifeLogWaitsAndIsNotRefused() = runBlocking {
        install(refusals = 0)
        H5600WorkoutIO.sink = { true }
        val ended = System.currentTimeMillis()
        val inner = H5600WorkoutIO.link
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) = inner.drsp(data)

            override fun bulk(data: ByteArray) {
                if (data.size == 3 && data.all { it.toInt() == 0 } && System.currentTimeMillis() - ended < 3_000) {
                    writes += "bulk 000000 refused"
                    H5600WorkoutIO.onConvoy("03 01")
                    return
                }
                inner.bulk(data)
            }
        }
        WatchExchange.onTransferEnded(ended)
        val result = withTimeoutOrNull(20_000) { H5600WorkoutIO.download() }
        assertNotNull(result)
        assertNull(result!!.error)
        assertTrue("no start refused: $writes", writes.none { it.endsWith("refused") })
        assertEquals(1, writes.count { it == "001c0000000000000000" })
    }

    @Test fun twoFailReasonsEndTheRead() = runBlocking {
        install(refusals = 2)
        H5600WorkoutIO.sink = { true }
        val result = withTimeoutOrNull(20_000) { H5600WorkoutIO.download(userAsked = true) }
        assertTrue(result?.error.orEmpty(), result?.error.orEmpty().contains("fail reason 1"))
        assertEquals(2, writes.count { it == "001c0000000000000000" })
    }
}
