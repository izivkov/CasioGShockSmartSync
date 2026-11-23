package org.avmedia.gshockapi.casio

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTransport : WatchTransport {
    val sent = CopyOnWriteArrayList<Pair<WatchChannel, List<Int>>>()
    val inFlight = AtomicInteger()
    val maxInFlight = AtomicInteger()
    @Volatile var ok = true
    @Volatile var hold: CompletableDeferred<Unit>? = null

    override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
        val now = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { maxOf(it, now) }
        sent += channel to frame.map { it.toInt() and 0xFF }
        delay(1)
        hold?.await()
        inFlight.decrementAndGet()
        return ok
    }
}

class WatchQueueTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transport = FakeTransport()
    private lateinit var queue: WatchQueue
    private val allFeatures = CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID

    @Before fun install() {
        CommandTable.strict = true
        queue = WatchQueue(scope, transport, readTimeoutMs = 400)
        WatchLink.queue = queue
        NotificationGate.reset()
        ConnectionKeepAlive.writer = ConnectionWriter
        ConnectionKeepAlive.clearEvents()
        ConnectionSetup.busy = { false }
        ConnectionSetup.reset()
    }

    @After fun restore() {
        CommandTable.strict = false
        ConnectionKeepAlive.writer = ConnectionWriter
        ConnectionSetup.busy = { org.avmedia.gshockapi.io.H5600HealthIO.isActive }
    }

    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 3_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what; sent=${transport.sent}" }
            Thread.sleep(5)
        }
    }

    private fun sentCodes() = transport.sent.map { it.second.first() }

    @Test fun oneAtATimeAndInTheOrderAskedUnderConcurrency() = runBlocking {
        val results = (0 until 4).map { p ->
            async(Dispatchers.Default) {
                (0 until 25).map { i -> queue.enqueue(GetSetMode.SET, b(0x13, p, i)) }.awaitAll()
            }
        }
        results.awaitAll()
        assertEquals(1, transport.maxInFlight.get())
        assertEquals(100, transport.sent.size)
        for (p in 0 until 4) {
            val mine = transport.sent.map { it.second }.filter { it[1] == p }.map { it[2] }
            assertEquals("producer $p keeps its order", (0 until 25).toList(), mine)
        }
    }

    @Test fun aReadHoldsTheQueueUntilItsAnswerAndLaterRequestsWait() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x28))
        val write = queue.enqueue(GetSetMode.SET, b(0x13, 1))
        waitFor("the read request") { sentCodes() == listOf(0x28) }
        delay(100)
        assertEquals("the write waits for the answer", listOf(0x28), sentCodes())
        queue.onNotification(allFeatures, b(0x28, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertTrue(read.await().ok)
        assertTrue(write.await().ok)
        assertEquals(listOf(0x28, 0x13), sentCodes())
    }

    @Test fun unsolicitedNotificationsAreHandledWhileAReadWaits() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x45))
        waitFor("the read request") { sentCodes() == listOf(0x45) }
        val dispatch = { data: String -> MessageDispatcher.onReceived(data, H5600Protocol) }
        dispatch("0x49 00")
        dispatch("0x47 02")
        dispatch("0x39 04") // handled (ignored for a connection kind that has no 0x39 exchange), never an error
        dispatch("0x3D 10")
        dispatch("0x28 00 00 00 00 B9 00 00 00 00")
        val events = ConnectionKeepAlive.recentEvents().joinToString("\n")
        assertTrue(events, events.contains("49 00 received, 49 01 written"))
        assertTrue(events, events.contains("47 02 received"))
        assertFalse("a pause command of 0x3D was taken in", NotificationGate.allowed())
        assertEquals("the condition gives the MTU", 185, NotificationGate.mtu)
        assertFalse("none of them ended the waiting read", read.isCompleted)
        queue.onNotification(allFeatures, b(0x45, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertTrue(read.await().ok)
        waitFor("49 01 and 00 01 behind the read") { transport.sent.map { it.second }.containsAll(listOf(listOf(0x49, 1), listOf(0x00, 1))) }
    }

    @Test fun linkLossIsTheFrame0001OnTheAllFeaturesCharacteristic() {
        ConnectionKeepAlive.onSetup("0x47 02")
        waitFor("the Link Loss write") { transport.sent.isNotEmpty() }
        assertEquals(listOf(WatchChannel.ALL_FEATURES to listOf(0x00, 0x01)), transport.sent.toList())
    }

    @Test fun aRefusalWithStatus81IsLoggedOnceAsNotSupported() = runBlocking {
        CommandTable.strict = false // 0x82 is not in the table: the watch itself refuses it
        repeat(2) {
            val read = queue.enqueue(GetSetMode.GET, b(0x82))
            waitFor("the read request") { sentCodes().size > it }
            queue.onNotification(allFeatures, b(0xFF, 0x81, 0x82))
            assertFalse(read.await().ok)
        }
        val lines = ConnectionKeepAlive.recentEvents().filter { it.contains("0x82 is not supported") || it.contains("request 82 failed") }
        assertEquals(lines.toString(), 1, lines.size)
        assertTrue(lines.single().contains("not supported"))
    }

    @Test fun theSmartAlarmAnswerOfFiveBytesIsLongEnough() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x50))
        waitFor("the read request") { sentCodes() == listOf(0x50) }
        queue.onNotification(allFeatures, b(0x50, 0x09, 0x00, 0x01, 0x3C))
        assertTrue(read.await().ok)
    }

    @Test fun anFFAnswerFailsAWaitingReadAndTheQueueGoesOn() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x45))
        val next = queue.enqueue(GetSetMode.SET, b(0x13, 1))
        waitFor("the read request") { sentCodes() == listOf(0x45) }
        queue.onNotification(allFeatures, b(0xFF, 0x45, 0x06))
        assertFalse(read.await().ok)
        assertTrue(next.await().ok)
    }

    @Test fun anFFDoesNotEndAWaitingWrite() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        transport.hold = gate
        val write = queue.enqueue(GetSetMode.SET, b(0x13, 1))
        waitFor("the write") { sentCodes() == listOf(0x13) }
        queue.onNotification(allFeatures, b(0xFF, 0x13, 0x06))
        delay(100)
        assertFalse(write.isCompleted)
        gate.complete(Unit)
        assertTrue(write.await().ok)
    }

    @Test fun aReadWithoutAnswerFailsAfterTheTimeoutAndNothingIsRetried() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x45))
        val next = queue.enqueue(GetSetMode.SET, b(0x13, 1))
        val outcome = withTimeout(3_000) { read.await() }
        assertFalse(outcome.ok)
        assertTrue(next.await().ok)
        assertEquals(listOf(0x45, 0x13), sentCodes())
    }

    @Test fun aFailedWriteResultIsReportedAndNotRetried() = runBlocking {
        transport.ok = false
        assertFalse(queue.enqueue(GetSetMode.SET, b(0x13, 1)).await().ok)
        assertEquals(1, transport.sent.size)
    }

    @Test fun aReadOfAWriteOnlyCodeNeverReachesTheRadio() = runBlocking {
        assertFalse(queue.enqueue(GetSetMode.GET, b(0x09)).await().ok)
        assertTrue(transport.sent.isEmpty())
    }

    @Test fun anAllFeaturesWriteGoesOutCutToTwentyBytes() = runBlocking {
        queue.enqueue(GetSetMode.SET, ByteArray(25) { 0x53 }).await()
        assertEquals(20, transport.sent.single().second.size)
    }

    @Test fun anAnswerToAnotherOptionByteDoesNotEndTheRead() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x1F, 1))
        waitFor("the request") { transport.sent.isNotEmpty() }
        queue.onNotification(allFeatures, b(0x1F, 0, 5, 5))
        delay(100)
        assertFalse(read.isCompleted)
        queue.onNotification(allFeatures, b(0x1F, 1, 5, 5))
        assertTrue(read.await().ok)
    }

    @Test fun aTooShortAnswerEndsTheReadAsFailed() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x28))
        waitFor("the request") { transport.sent.isNotEmpty() }
        queue.onNotification(allFeatures, b(0x28, 1))
        assertFalse(read.await().ok)
    }

    @Test fun onlyTheAllFeaturesCharacteristicCanAnswer() = runBlocking {
        val read = queue.enqueue(GetSetMode.GET, b(0x28))
        waitFor("the request") { transport.sent.isNotEmpty() }
        queue.onNotification(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, b(0x28, 1, 2, 3, 4, 5, 6, 7, 8, 9))
        delay(100)
        assertFalse(read.isCompleted)
        queue.clear("test over")
        assertFalse(read.await().ok)
    }
}
