package org.avmedia.gshockapi.io

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.casio.WatchLink
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * After step 3 the watch answers the group header
 * request with `00 1D` + address + length on the data request characteristic, two bulk chunks (247 and 13 bytes, data 256 +
 * CRC `22 33`), then `09 1D` (all sent). Our read never answered and stopped 10 s later. Every frame here goes through the real
 * routing (characteristic + [H5600Protocol] + [MessageDispatcher]), not straight into the transfer's inbox.
 */
class H5600WorkoutGroupHeaderTest {
    private val savedSink = H5600WorkoutIO.sink
    private val savedLink = H5600WorkoutIO.link
    private val writes = CopyOnWriteArrayList<String>()
    private val keysOfSettingsAnswers = CopyOnWriteArrayList<Int?>()

    private val drspChar = CasioConstants.CASIO_DATA_REQUEST_CHARACTERISTIC_UUID
    private val bulkChar = CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID
    private val allFeatures = CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID

    private fun hex(b: ByteArray) = b.joinToString(" ", prefix = "0x") { "%02X".format(it) }

    private fun deliver(char: UUID, frame: ByteArray) =
        WatchLink.withSource(char) { MessageDispatcher.onReceived(hex(frame), H5600Protocol) }

    /** The 256 data bytes of the first group header chunk as received (before inversion): `01` and `07` at 13, the rest 0. */
    private val groupChunk = ByteArray(256).also { it[0] = 1; it[13] = 7 }

    private fun bulkFrames(): List<ByteArray> {
        val crc = WorkoutCodec.crc16Kermit(groupChunk, 0, 256)
        val raw = groupChunk + byteArrayOf(crc.toByte(), (crc shr 8).toByte())
        return listOf(byteArrayOf(5) + raw.copyOfRange(0, 246), byteArrayOf(5) + raw.copyOfRange(246, 258))
    }

    @Before fun install() {
        ConnectionSetup.reset()
        ConnectionSetup.onWatchSetupDone()
        H5600WorkoutIO.resetBackoff()
        H5600WorkoutIO.sink = { true }
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) {
                writes += data.joinToString("") { "%02x".format(it) }
                if (data.size != 10 || data[0].toInt() != 0) return
                when (data[1].toInt()) {
                    0x1C -> deliver(drspChar, data.copyOf())
                    0x1D -> {
                        deliver(drspChar, data.copyOf())
                        // A settings answer in the middle of the transfer (`11 00 00 00 06 ...`).
                        keysOfSettingsAnswers += WatchLink.withSource(allFeatures) { H5600Protocol.extractKey("0x11 00 00 00 06 00 00 00 00 00 00") }
                        bulkFrames().forEach { deliver(bulkChar, it) }
                        deliver(drspChar, byteArrayOf(9) + data.copyOfRange(1, 10))
                    }
                }
            }

            override fun bulk(data: ByteArray) {
                writes += "bulk " + data.joinToString("") { "%02x".format(it) }
                when (data[0].toInt()) {
                    0 -> deliver(bulkChar, byteArrayOf(0, 0, 0))
                    4 -> deliver(bulkChar, ByteArray(10).also { it[0] = 4 })
                    6 -> deliver(bulkChar, byteArrayOf(6, 0xFA.toByte(), 0, 0, 0x10, 0, 0, 0, 2))
                }
            }
        }
    }

    @After fun restore() {
        H5600WorkoutIO.sink = savedSink
        H5600WorkoutIO.link = savedLink
        H5600WorkoutIO.resetBackoff()
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    @Test fun theFramesOfTheDumpChecksumAsTheWatchSentThem() {
        val crc = WorkoutCodec.crc16Kermit(groupChunk, 0, 256)
        assertEquals("22 33", "%02X %02X".format(crc and 0xFF, crc shr 8))
        assertEquals(listOf(247, 13), bulkFrames().map { it.size })
    }

    @Test fun theGroupHeaderIsAnsweredAndAllFourPartsAreRead() = runBlocking {
        val read = async(Dispatchers.IO) { runCatching { H5600WorkoutIO.download(userAsked = true) } }
        // Bounded: the broken routing waits 10 s per try for a `09 1D` that never reaches the read.
        val outcome = withTimeoutOrNull(15_000) { read.await() }
        read.cancel()
        val drsp = writes.filter { !it.startsWith("bulk") }
        val events = AppLog.recentEvents().filter { it.contains("Workout read") || it.contains("workout rx") }.takeLast(30)
        val why = "outcome ${outcome?.map { it?.error }} drsp $drsp log $events"
        assertTrue("group header read: $why", events.any { it.contains("1D group header read") })
        for (addr in listOf("00000000", "00010000", "00020000", "00030000")) {
            val at = drsp.indexOf("001d${addr}00010000")
            assertTrue("request at $addr: $why", at >= 0)
            assertEquals("answer to the read at $addr: $why", listOf("091d", "041d"), drsp.subList(at + 1, at + 3))
        }
        assertTrue("no abort of a read: $why", drsp.none { it == "031d" })
        assertTrue("0x11 keeps its key: $keysOfSettingsAnswers", keysOfSettingsAnswers.isNotEmpty() && keysOfSettingsAnswers.all { it == 0x11 })
        // The next log must show every bulk frame whole, not the first two bytes.
        val whole = "workout rx bulk " + bulkFrames()[1].joinToString(" ") { b -> "%02X".format(b) }
        assertTrue("whole bulk frame logged: $why", AppLog.recentEvents().any { it.contains(whole) })
    }
}
