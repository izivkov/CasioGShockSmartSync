package org.avmedia.gshockapi.io

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A fake wire. [onRequest] plays the watch: it calls [RawSettingIO.offer] the way MessageDispatcher does for every notification. */
class FakeSettingLink : SettingLink {
    val requests = ArrayList<List<Int>>()
    val packets = ArrayList<List<Int>>()
    val checked = ArrayList<List<Int>>()
    var onRequest: (ByteArray) -> Unit = {}
    var onChecked: (ByteArray) -> Unit = {}
    var confirm = true

    override fun sendRequest(bytes: ByteArray) {
        requests += bytes.map { it.toInt() and 0xFF }
        onRequest(bytes)
    }

    override fun sendPacket(packet: IntArray) {
        packets += packet.toList()
    }

    override suspend fun sendChecked(bytes: ByteArray): Boolean {
        checked += bytes.map { it.toInt() and 0xFF }
        onChecked(bytes)
        return confirm
    }
}

/** The one settings reader: moved from the app's RawWatchRequestTest (same bytes, same outcomes), plus the single write path. */
class RawSettingIOTest {
    private val link = FakeSettingLink()

    @Before fun install() {
        RawSettingIO.link = link
        RawSettingIO.settleMs = 0
    }

    @After fun release() {
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
        RawSettingIO.link = AndroidSettingLink
        RawSettingIO.settleMs = 1_000L
    }

    /** The notifications that [offer] did not take: what MessageDispatcher hands to the library. */
    private val passedOn = ArrayList<String>()

    private fun notify(data: String): Boolean = RawSettingIO.offer(data).also { if (!it) passedOn += data }

    @Test fun aTransferInTheMiddleOfAReadStillGetsItsNotifications() = runBlocking {
        link.onRequest = {
            // The transfer's DRSP and CONVOY notifications arrive while the read waits; the answer comes between them.
            notify("0x00 2E 01 10 00 00")
            notify("0x05 AA BB")
            notify("0x41 01 02 03")
            notify("0x05 CC DD")
        }
        val reply = RawSettingIO.read(byteArrayOf(0x41), 2_000)
        assertArrayEquals(byteArrayOf(0x41, 1, 2, 3), reply.frame)
        assertEquals(listOf("0x00 2E 01 10 00 00", "0x05 AA BB", "0x05 CC DD"), passedOn)
    }

    @Test fun aRequestOfSeveralBytesIsSentWhole() = runBlocking {
        link.onRequest = { notify("0x54 01 40 0A E8 03"); notify("0x05 AA") }
        val reply = RawSettingIO.read(byteArrayOf(0x54, 0, 1, 0x40, 0), 2_000)
        assertEquals(listOf(listOf(0x54, 0, 1, 0x40, 0)), link.requests)
        assertArrayEquals(byteArrayOf(0x54, 1, 0x40, 0x0A, 0xE8.toByte(), 3), reply.frame)
        assertEquals(listOf("0x05 AA"), passedOn)
    }

    @Test fun theNotificationManagerIsAskedAs3D30() = runBlocking {
        link.onRequest = { notify("0x3D 30 00 02") }
        RawSettingIO.read(RawSettingIO.requestFor(0x3D), 2_000)
        assertEquals(listOf(listOf(0x3D, 0x30)), link.requests)
    }

    @Test fun theErrorAnswerForTheCodeIsAFailureAndNothingIsPassedOn() = runBlocking {
        link.onRequest = { assertTrue(notify("0xFF 41")); assertTrue(notify("0xFF 99")) }
        val reply = RawSettingIO.read(byteArrayOf(0x41), 2_000)
        assertNull(reply.frame)
        assertTrue(reply.failure!!, reply.failure!!.contains("refused"))
        assertEquals(emptyList<String>(), passedOn)
    }

    @Test fun aNotificationWhenNoReadWaitsGoesToTheLibrary() = runBlocking {
        RawSettingIO.read(byteArrayOf(0x41), 300)
        assertFalse(RawSettingIO.offer("0x05 11"))
        assertFalse(RawSettingIO.offer("0x41 01 02"))
        assertFalse(RawSettingIO.offer("0xFF 41"))
    }

    @Test fun noReadStartsWhileATransferHoldsTheLink() = runBlocking {
        WatchExchange.mutex.lock()
        val reply = RawSettingIO.read(byteArrayOf(0x41), 2_000)
        assertNull(reply.frame)
        assertTrue(reply.failure!!, reply.failure!!.contains("busy"))
        assertTrue("nothing may be written during a transfer", link.requests.isEmpty())
    }

    @Test fun aTransferWaitsForARunningRead() = runBlocking {
        val read = async { RawSettingIO.read(byteArrayOf(0x41), 1_500) }
        while (link.requests.isEmpty()) delay(10)
        assertFalse("the read holds the link", WatchExchange.mutex.tryLock())
        assertTrue(RawSettingIO.offer("0x41 01 02"))
        read.await()
        assertTrue(WatchExchange.mutex.tryLock())
    }

    @Test fun anAnswerIsMatchedByTheCodeOnly() = runBlocking {
        fun offered(data: String): Boolean {
            var taken = false
            link.onRequest = { taken = RawSettingIO.offer(data) }
            runBlocking { RawSettingIO.read(byteArrayOf(0x41), 100) }
            return taken
        }
        assertTrue(offered("0x41 01"))
        assertTrue(offered("41 01"))
        assertFalse(offered("0x05 41"))
        assertFalse(offered("junk"))
    }

    @Test fun aReplyAfterFiveSecondsIsStillAccepted() = runBlocking {
        link.onRequest = { GlobalScope.launch { delay(5_000); notify("0x45 01 02 03 04 05 06 07 08 09") } }
        val reply = RawSettingIO.read(byteArrayOf(0x45))
        assertTrue(reply.failure, reply.frame != null)
        assertEquals(10, reply.frame!!.size)
    }

    @Test fun theReadWaitsTenSecondsAsCasioDoes() {
        assertEquals(10_000L, RawSettingIO.READ_TIMEOUT_MS)
    }

    @Test fun anFFAnswerIsAFailureCarryingItsStatus() = runBlocking {
        link.onRequest = { notify("0xFF 0A") }
        val reply = RawSettingIO.read(byteArrayOf(0x42), 2_000)
        assertNull(reply.frame)
        assertTrue(reply.failure!!, reply.failure!!.contains("status 0A"))
        assertEquals(0x0A, RawSettingIO.failureStatus(intArrayOf(0xFF, 0x42, 0x0A), 0x42))
        assertEquals(0x0A, RawSettingIO.failureStatus(intArrayOf(0xFF, 0x0A), 0x42))
    }

    @Test fun noAnswerAtAllIsReportedWithTheWaitingTime() = runBlocking {
        val reply = RawSettingIO.read(byteArrayOf(0x42), 300)
        assertNull(reply.frame)
        assertTrue(reply.failure!!.contains("did not answer"))
    }

    @Test fun anAnswerForAnotherItemOfTheSameCodeIsNotTaken() = runBlocking {
        link.onRequest = { notify("0x4E 02 AA"); notify("0x4E 01 BB") }
        val reply = RawSettingIO.read(byteArrayOf(0x4E, 1), 2_000) { it[1].toInt() == 1 }
        assertArrayEquals(byteArrayOf(0x4E, 1, 0xBB.toByte()), reply.frame)
    }

    @Test fun twoReadersOfTheSameRequestShareOneRequest() = runBlocking {
        val a = async { RawSettingIO.read(byteArrayOf(0x41), 1_500) }
        val b = async { RawSettingIO.read(byteArrayOf(0x41), 1_500) }
        while (link.requests.isEmpty()) delay(10)
        delay(50)
        assertTrue(RawSettingIO.offer("0x41 07 08"))
        assertArrayEquals(byteArrayOf(0x41, 7, 8), a.await().frame)
        assertArrayEquals(byteArrayOf(0x41, 7, 8), b.await().frame)
        assertEquals(1, link.requests.size)
    }

    // ---- the one write path ----

    /** A watch that keeps [value] and answers a read of its code with it; [keeps] false drops every write. */
    private fun watch(initial: ByteArray, keeps: Boolean = true): () -> ByteArray {
        var value = initial.copyOf()
        link.onRequest = { req -> if (req[0] == value[0]) notify(value.joinToString(" ", "0x") { "%02X".format(it) }) }
        link.onChecked = { frame -> if (keeps) value = frame.copyOf() }
        return { value }
    }

    private val face51 = byteArrayOf(0x51, 1, 2, 2, 2, 2)

    @Test fun aFaceWriteIsByteExactAndReadBack() = runBlocking {
        val value = watch(face51)
        val result = RawSettingIO.modifySafe(0x51, setOf(1)) { it.also { f -> f[1] = 2 } }
        assertEquals(SettingWriteResult.Done, result)
        assertEquals(listOf(listOf(0x51, 2, 2, 2, 2, 2)), link.checked)
        assertEquals("one read before, one read back", 2, link.requests.size)
        assertArrayEquals(byteArrayOf(0x51, 2, 2, 2, 2, 2), value())
    }

    @Test fun aFaceWriteTheWatchDoesNotKeepIsRefusedOnceWithoutARetry() = runBlocking {
        watch(face51, keeps = false)
        val result = RawSettingIO.modifySafe(0x51, setOf(1)) { it.also { f -> f[1] = 2 } }
        assertTrue(result is SettingWriteResult.Failed)
        assertEquals(1, link.checked.size)
    }

    @Test fun aChangeOutsideTheTouchedBytesIsRefusedBeforeTheWrite() = runBlocking {
        watch(face51)
        val result = RawSettingIO.modifySafe(0x51, setOf(1)) { it.also { f -> f[1] = 2; f[5] = 9 } }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(link.checked.isEmpty())
    }

    @Test fun aWriteTheWatchDoesNotConfirmIsReported() = runBlocking {
        watch(face51)
        link.confirm = false
        val result = RawSettingIO.modifySafe(0x51, setOf(1)) { it.also { f -> f[1] = 2 } }
        assertTrue(result is SettingWriteResult.Failed)
    }

    @Test fun theSameCodeIsNeverChangedByTwoWritersAtOnce() = runBlocking {
        val value = watch(face51)
        val a = async { RawSettingIO.modifySafe(0x51, setOf(1)) { it.also { f -> f[1] = 2 } } }
        val b = async { RawSettingIO.modifySafe(0x51, setOf(2)) { it.also { f -> f[2] = 5 } } }
        a.await(); b.await()
        // the second change read what the first had written: both are in the watch
        assertArrayEquals(byteArrayOf(0x51, 2, 5, 2, 2, 2), value())
    }
}
