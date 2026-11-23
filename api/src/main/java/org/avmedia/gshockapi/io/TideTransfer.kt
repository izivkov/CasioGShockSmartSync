package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.casio.LongProcess

/** The bytes of the port record transfer (notes I5, bulk category 0x23), without any I/O. */
object TideFraming {
    const val CATEGORY = 0x23
    private const val KIND_START = 0x00
    private const val KIND_SIZE_A = 0x02
    private const val KIND_SIZE_B = 0x06
    const val KIND_DATA = 0x05
    const val MAX_CHUNK = 512

    /** `00 23` + the record length as 3 bytes, low byte first (data request characteristic). */
    fun start(length: Int): ByteArray =
        byteArrayOf(0, CATEGORY.toByte(), length.toByte(), (length shr 8).toByte(), (length shr 16).toByte())

    fun end(): ByteArray = byteArrayOf(0x04, CATEGORY.toByte())

    fun abort(): ByteArray = byteArrayOf(0x03, CATEGORY.toByte())

    /** The watch's answer to the start (bulk): `00 state reason`. State 0 = go on. Null when it is not that message. */
    fun parseStart(bulk: ByteArray): Pair<Int, Int>? =
        if (bulk.size >= 3 && bulk[0].toInt() == KIND_START) (bulk[1].toInt() and 0xFF) to (bulk[2].toInt() and 0xFF) else null

    /** The data bytes per chunk from the watch's size message (kind 2 or 6, size u16 low first, minus 4); null when it is not that or unusable. */
    fun parseChunkSize(bulk: ByteArray): Int? {
        if (bulk.size < 3) return null
        val kind = bulk[0].toInt()
        if (kind != KIND_SIZE_A && kind != KIND_SIZE_B) return null
        val size = (bulk[1].toInt() and 0xFF) or ((bulk[2].toInt() and 0xFF) shl 8)
        return (size - 4).takeIf { it in 1..MAX_CHUNK }
    }

    /** `05` + the next [chunk] bytes, plain (no checksum), until the record is used up. */
    fun chunks(record: ByteArray, chunk: Int): List<ByteArray> {
        require(chunk > 0)
        return record.toList().chunked(chunk).map { byteArrayOf(KIND_DATA.toByte()) + it.toByteArray() }
    }
}

class TideRunResult(val error: String?)

/**
 * Sends one port record (notes I5, steps 3 to 7). The caller holds the exchange lock and writes `2E 02` afterwards.
 * Any failure writes the abort `03 23` and returns an error text; nothing is retried.
 */
class TideTransfer(
    private val link: WorkoutLink,
    private val inbox: Channel<WorkoutMsg>,
    private val replyTimeoutMs: Long = LongProcess.TIDE.answerMs!!,
    private val chunkGapMs: Long = LongProcess.TIDE.chunkGapMs!!,
    private val endTimeoutMs: Long = LongProcess.TIDE.totalMs!!,
) {
    suspend fun run(record: ByteArray): TideRunResult {
        ConnectionKeepAlive.log("tide: start 00 23, record ${record.size} bytes")
        link.drsp(TideFraming.start(record.size))
        val start = awaitBulk(replyTimeoutMs) { TideFraming.parseStart(it) != null }?.let { TideFraming.parseStart(it) }
            ?: return fail("The watch did not answer the start of the transfer.")
        ConnectionKeepAlive.log("tide: start answer state ${start.first} reason ${start.second}")
        if (start.first != 0) return fail("The watch refused the tide transfer (state ${start.first}, reason ${start.second}).")
        val chunk = awaitBulk(replyTimeoutMs) { TideFraming.parseChunkSize(it) != null }?.let { TideFraming.parseChunkSize(it) }
            ?: return fail("The watch did not say how big the pieces are, or said a size the app does not accept.")
        ConnectionKeepAlive.log("tide: chunk size $chunk, ${TideFraming.chunks(record, chunk).size} pieces")
        for (piece in TideFraming.chunks(record, chunk)) {
            // The next piece goes chunkGapMs after the write result of this one (notes T4); a failed write ends the transfer.
            if (!link.bulkConfirmed(piece)) return fail("A piece of the tide data could not be sent to the watch.")
            delay(chunkGapMs)
        }
        ConnectionKeepAlive.log("tide: all pieces sent, waiting up to ${endTimeoutMs / 1000} s for the end message")
        val end = awaitChannel(endTimeoutMs, H5600Channel.DRSP) { true }
            ?: return fail("The watch did not confirm the end of the transfer.")
        ConnectionKeepAlive.log("tide: end message " + end.take(8).joinToString(" ") { "%02X".format(it) })
        if (end.firstOrNull()?.toInt() == 0x03) return fail("The watch aborted the transfer.")
        link.drsp(TideFraming.end())
        return TideRunResult(null)
    }

    private fun fail(text: String): TideRunResult {
        link.drsp(TideFraming.abort())
        return TideRunResult(text)
    }

    private suspend fun awaitBulk(timeoutMs: Long, accept: (ByteArray) -> Boolean): ByteArray? =
        awaitChannel(timeoutMs, H5600Channel.CONVOY, accept)

    /** The first message on [channel] that [accept] takes; messages on the other channel are dropped. Null on timeout. */
    private suspend fun awaitChannel(timeoutMs: Long, channel: H5600Channel, accept: (ByteArray) -> Boolean): ByteArray? =
        withTimeoutOrNull(timeoutMs) {
            var found: ByteArray? = null
            while (found == null) {
                val m = inbox.receive()
                if (m.channel == channel && accept(m.data)) found = m.data
            }
            found
        }
}
