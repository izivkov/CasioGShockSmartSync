package org.avmedia.gshockGoogleSync.webhook

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.io.WatchExchange
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake link: [library] is the handler the library installed; every notification the "watch" sends goes to whatever handler is set. */
private class FakeLink(library: IDataReceived) : RawLink {
    var current: IDataReceived? = library
    val forwarded = ArrayList<String>()
    val written = ArrayList<Int>()
    var onWrite: (Int) -> Unit = {}
    override fun callback() = current
    override fun setCallback(callback: IDataReceived) { current = callback }
    override fun forward(data: String) { forwarded += data }
    override fun write(code: Int) { written += code; onWrite(code) }
    val writtenBytes = ArrayList<List<Int>>()
    override fun writeBytes(bytes: ByteArray) { writtenBytes += bytes.map { it.toInt() and 0xFF }; onWrite(bytes[0].toInt() and 0xFF) }
    fun notify(data: String) = current?.dataReceived(data)
}

class RawWatchRequestTest {
    @After fun release() { if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock() }

    private val libraryHandler = IDataReceived { }

    @Test fun aTransferInTheMiddleOfARawReadStillGetsItsNotifications() = runBlocking {
        val link = FakeLink(libraryHandler)
        val raw = RawWatchRequest(link)
        link.onWrite = {
            // The transfer's DRSP and CONVOY notifications arrive while the raw read waits; the answer comes between them.
            link.notify("0x00 2E 01 10 00 00")
            link.notify("0x05 AA BB")
            link.notify("0x41 01 02 03")
            link.notify("0x05 CC DD")
        }
        val answers = raw.request(0x41, 2_000)
        assertEquals(listOf("0x41 01 02 03"), answers)
        assertEquals(listOf("0x00 2E 01 10 00 00", "0x05 AA BB", "0x05 CC DD"), link.forwarded)
    }

    @Test fun aRequestOfSeveralBytesIsSentWhole() = runBlocking {
        val link = FakeLink(libraryHandler)
        link.onWrite = { link.notify("0x54 01 40 0A E8 03"); link.notify("0x05 AA") }
        val answers = RawWatchRequest(link).request(byteArrayOf(0x54, 0, 1, 0x40, 0), 2_000)
        assertEquals(listOf(listOf(0x54, 0, 1, 0x40, 0)), link.writtenBytes)
        assertEquals(emptyList<Int>(), link.written)
        assertEquals(listOf("0x54 01 40 0A E8 03"), answers)
        assertEquals(listOf("0x05 AA"), link.forwarded)
    }

    @Test fun theNotificationManagerIsAskedAs3D30InTheRawRead() = runBlocking {
        val link = FakeLink(libraryHandler)
        link.onWrite = { link.notify("0x3D 30 00 02") }
        RawWatchRequest(link).request(0x3D, 2_000)
        assertEquals(listOf(listOf(0x3D, 0x30)), link.writtenBytes)
    }

    @Test fun theErrorAnswerForTheCodeIsKeptToo() = runBlocking {
        val link = FakeLink(libraryHandler)
        link.onWrite = { link.notify("0xFF 41"); link.notify("0xFF 99") }
        val answers = RawWatchRequest(link).request(0x41, 2_000)
        // Notes D2: any FF answer while a read waits is that read's failure, so both are kept.
        assertEquals(listOf("0xFF 41", "0xFF 99"), answers)
        assertEquals(emptyList<String>(), link.forwarded)
    }

    @Test fun theHandlerThatWasInstalledIsPutBackNotANewOne() = runBlocking {
        val link = FakeLink(libraryHandler)
        RawWatchRequest(link).request(0x41, 300)
        assertSame(libraryHandler, link.current)
    }

    @Test fun aNotificationAfterTheReadGoesToTheLibraryHandler() = runBlocking {
        val seen = ArrayList<String>()
        val link = FakeLink(IDataReceived { d -> seen += d!! })
        RawWatchRequest(link).request(0x41, 300)
        link.notify("0x05 11")
        assertEquals(listOf("0x05 11"), seen)
    }

    @Test fun noRawReadStartsWhileATransferHoldsTheLink() = runBlocking {
        val link = FakeLink(libraryHandler)
        WatchExchange.mutex.lock()
        val answers = RawWatchRequest(link).request(0x41, 2_000)
        assertTrue(answers.isEmpty())
        assertTrue("nothing may be written during a transfer", link.written.isEmpty())
        assertSame(libraryHandler, link.current)
    }

    @Test fun aTransferWaitsForARunningRawRead() = runBlocking {
        val link = FakeLink(libraryHandler)
        val read = async { RawWatchRequest(link).request(0x41, 1_500) }
        while (link.written.isEmpty()) delay(10)
        assertFalse("the raw read holds the link", WatchExchange.mutex.tryLock())
        read.await()
        assertTrue(WatchExchange.mutex.tryLock())
    }

    @Test fun answersToMatchesTheCodeOnly() {
        assertTrue(RawWatchRequest.answersTo("0x41 01", 0x41))
        assertTrue(RawWatchRequest.answersTo("41 01", 0x41))
        assertFalse(RawWatchRequest.answersTo("0x05 41", 0x41))
        assertFalse(RawWatchRequest.answersTo("junk", 0x41))
    }

    @Test fun aReplyAfterFiveSecondsIsStillAccepted() = runBlocking {
        val link = FakeLink(libraryHandler)
        val raw = RawWatchRequest(link)
        link.onWrite = {
            kotlinx.coroutines.GlobalScope.launch { delay(5_000); link.notify("0x45 01 02 03 04 05 06 07 08 09") }
        }
        val reply = raw.read(byteArrayOf(0x45))
        assertTrue(reply.failure, reply.frame != null)
        assertEquals(10, reply.frame!!.size)
    }

    @Test fun theReadWaitsTenSecondsAsCasioDoes() {
        assertEquals(10_000L, RawWatchRequest.READ_TIMEOUT_MS)
    }

    @Test fun anFFAnswerIsAFailureCarryingItsStatus() = runBlocking {
        val link = FakeLink(libraryHandler)
        link.onWrite = { link.notify("0xFF 0A") }
        val reply = RawWatchRequest(link).read(byteArrayOf(0x42), 2_000)
        assertEquals(null, reply.frame)
        assertTrue(reply.failure!!, reply.failure!!.contains("status 0A"))
        assertEquals(0x0A, RawWatchRequest.failureStatus("0xFF 42 0A", 0x42))
        assertEquals(0x0A, RawWatchRequest.failureStatus("0xFF 0A", 0x42))
        assertEquals(null, RawWatchRequest.failureStatus("0x42 01", 0x42))
    }

    @Test fun noAnswerAtAllIsReportedWithTheWaitingTime() = runBlocking {
        val reply = RawWatchRequest(FakeLink(libraryHandler)).read(byteArrayOf(0x42), 300)
        assertEquals(null, reply.frame)
        assertTrue(reply.failure!!.contains("did not answer"))
    }
}
