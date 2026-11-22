package org.avmedia.gshockapi.io

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.model.WorkoutSession

/** Where the transfer writes; the app wires it to the two characteristics. */
interface WorkoutLink {
    /** The data request characteristic (`cmd cat` and so on). */
    fun drsp(data: ByteArray)

    /** The bulk (convoy) characteristic: first byte = kind. */
    fun bulk(data: ByteArray)
}

class WorkoutMsg(val channel: H5600Channel, val data: ByteArray)

/** What one run did. [error] null = finished (also with nothing to fetch). */
class WorkoutRunResult(val downloaded: Int, val error: String?, val refused: Boolean = false)

/**
 * Steps 1 to 8 of the workout transfer (notes H1) on a [WorkoutLink]. The caller holds the exchange lock.
 * Each workout goes to [sink] first; only when the sink took it (true) is the fetched mark written for it, so a
 * workout that could not be stored stays on the watch for the next run. [capture] receives every message and the
 * decoded bytes of each read, for the raw dump.
 */
class WorkoutTransfer(
    private val link: WorkoutLink,
    private val inbox: Channel<WorkoutMsg>,
    private val sink: suspend (WorkoutSession) -> Boolean,
    private val blockCache: MutableMap<String, ByteArray> = HashMap(),
    private val capture: (WorkoutMsg?, ByteArray?) -> Unit = { _, _ -> },
    private val replyTimeoutMs: Long = 5_000L,
    private val readTimeoutMs: Long = 10_000L,
) {
    private class Fail(val text: String, val refused: Boolean = false) : Exception(text)

    suspend fun run(): WorkoutRunResult {
        var done = 0
        try {
            start()
            val header = readGroup()
            val pending = WorkoutGroup.pendingSlots(header)
            val transferred = WorkoutGroup.transferredSlots(header).toMutableSet()
            val headers = pending.mapNotNull { slot -> readHeader(slot)?.let { slot to it } }
            if (headers.size < pending.size) throw Fail("a workout header could not be read")
            val sorted = headers.sortedBy { it.second.startUtcMillis ?: Long.MAX_VALUE }
            for ((index, item) in sorted.withIndex()) {
                val (slot, hdr) = item
                val session = fetchWorkout(slot, hdr)
                if (runCatching { sink(session) }.isFailure) throw Fail("the workout could not be stored; it stays on the watch")
                transferred += slot
                link.bulk(WorkoutGroup.fetchedMark(transferred, noneLeft = index == sorted.lastIndex))
                awaitBulk(KIND_MARK) ?: throw Fail("no answer to the fetched mark")
                blockCache.keys.removeAll { it.startsWith("${hdr.startUtcMillis}:") }
                done++
            }
            finish(abort = false)
            return WorkoutRunResult(done, null)
        } catch (f: Fail) {
            finish(abort = true)
            return WorkoutRunResult(done, f.text, f.refused)
        }
    }

    private suspend fun start() {
        link.drsp(request(CAT_SESSION, 0L, 0))
        var tries = 0
        while (true) {
            link.bulk(byteArrayOf(0, 0, 0))
            val reply = awaitBulk(KIND_START) ?: throw Fail("the watch did not answer the start")
            val state = reply.getOrElse(1) { 0 }.toInt() and 0xFF
            val reason = reply.getOrElse(2) { 0 }.toInt() and 0xFF
            if (state == 0) break
            when (reason) {
                1 -> throw Fail("the watch battery is too low for the workout transfer", refused = true)
                2 -> throw Fail("the watch is too cold for the workout transfer", refused = true)
                else -> if (++tries >= BUSY_TRIES) throw Fail("the watch stayed busy (reason $reason)", refused = true) else delay(BUSY_RETRY_MS)
            }
        }
        link.bulk(ByteArray(10).also { it[0] = 4 })
        awaitBulk(KIND_PARAMS) ?: throw Fail("no answer to the connection parameters")
        link.bulk(byteArrayOf(4, 1, 0x18, 0, 0x18, 0, 0, 0, 0xDC.toByte(), 5))
        awaitBulk(KIND_PARAMS) ?: throw Fail("no answer to the interval")
        link.bulk(ByteArray(14).also { it[0] = 6 })
        val parameters = awaitBulk(KIND_SIZES) ?: throw Fail("no answer to the transfer parameters")
        link.bulk(parameters)
        awaitBulk(KIND_SIZES)
    }

    private suspend fun readGroup(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until 4) out.write(read(CAT_GROUP, i * 256L, 256, WorkoutChunkAssembler.HEADER_CHUNK) ?: throw Fail("the group header could not be read"))
        return out.toByteArray()
    }

    private suspend fun readHeader(slot: Int): WorkoutHeader? =
        read(CAT_HEADER, HEADERS_FROM + 256L * slot, 256, WorkoutChunkAssembler.HEADER_CHUNK)?.let { WorkoutHeader.decode(it) }

    private suspend fun fetchWorkout(slot: Int, h: WorkoutHeader): WorkoutSession {
        val data = chain(h, CAT_DATA, h.dataAddress, h.dataBytes) ?: throw Fail("workout in slot $slot: data blocks could not be read")
        val laps = chain(h, CAT_LAPS, h.lapAddress, h.lapBytes) ?: throw Fail("workout in slot $slot: lap blocks could not be read")
        // The one-minute walking laps (header offset 55) are read to keep the chain complete; they are not decoded yet.
        chain(h, CAT_LAPS, h.minuteLapAddress, h.minuteLapBytes) ?: throw Fail("workout in slot $slot: minute lap blocks could not be read")
        return WorkoutSessionBuilder.build(slot, h, data, laps)
    }

    /** Reads a block chain. Blocks already read for the same workout (same start time) are not asked for again. */
    private suspend fun chain(h: WorkoutHeader, category: Int, first: Long, announced: Long): List<ByteArray>? {
        if (announced == 0L || first == WorkoutHeader.NO_ADDRESS) return emptyList()
        val blocks = ArrayList<ByteArray>()
        val seen = HashSet<Long>()
        var addr: Long? = first
        while (addr != null && seen.add(addr) && blocks.size < MAX_BLOCKS) {
            val key = "${h.startUtcMillis}:$category:$addr"
            val block = blockCache[key] ?: (read(category, addr, WorkoutRecords.BLOCK_SIZE, WorkoutChunkAssembler.BLOCK_CHUNK) ?: return null)
            blockCache[key] = block
            blocks += block
            addr = WorkoutRecords.nextAddress(block)
        }
        return blocks
    }

    /** One read (`00 cat address length`) with sector answers, one retry of a sector and one retry of the whole read. */
    private suspend fun read(category: Int, address: Long, length: Int, chunk: Int): ByteArray? {
        repeat(2) {
            val asm = WorkoutChunkAssembler(length, chunk)
            link.drsp(request(category, address, length))
            var bad = 0
            while (true) {
                val m = next(readTimeoutMs) ?: return null
                if (m.channel == H5600Channel.CONVOY) {
                    if (m.data.size > 1 && m.data[0] == BULK_DATA) asm.feed(m.data.copyOfRange(1, m.data.size))
                    continue
                }
                when (m.data.firstOrNull()?.toInt()) {
                    SECTOR_END ->
                        if (asm.endSector()) {
                            link.drsp(byteArrayOf(SECTOR_END.toByte(), category.toByte()))
                        } else {
                            if (++bad >= 2) return null
                            asm.dropSector()
                            link.drsp(byteArrayOf(0x08, category.toByte()))
                            link.drsp(byteArrayOf(0x06, category.toByte()))
                        }
                    DROP_SECTOR -> asm.dropSector()
                    ALL_SENT -> {
                        link.drsp(byteArrayOf(ALL_SENT.toByte(), category.toByte()))
                        link.drsp(byteArrayOf(0x04, category.toByte()))
                        val result = asm.result()
                        if (result != null) {
                            capture(null, result)
                            return result
                        }
                        link.drsp(byteArrayOf(0x03, category.toByte()))
                        return@repeat
                    }
                    ABORTED -> return null
                }
            }
        }
        return null
    }

    private suspend fun next(timeoutMs: Long): WorkoutMsg? =
        withTimeoutOrNull(timeoutMs) { inbox.receive() }?.also { capture(it, null) }

    private suspend fun awaitBulk(kind: Int): ByteArray? {
        while (true) {
            val m = next(replyTimeoutMs) ?: return null
            if (m.channel == H5600Channel.CONVOY && m.data.firstOrNull()?.toInt() == kind) return m.data
        }
    }

    private suspend fun finish(abort: Boolean) {
        link.bulk(ByteArray(10).also { it[0] = 4 })
        awaitBulk(KIND_PARAMS)
        link.bulk(byteArrayOf(4, 1, 0x30, 1, 0x40, 1, 4, 0, 0xDC.toByte(), 5))
        awaitBulk(KIND_PARAMS)
        // Abort inside the block categories names the session category, as Casio's app does.
        link.drsp(byteArrayOf(if (abort) 0x03 else 0x04, CAT_SESSION.toByte()))
    }

    private fun request(category: Int, address: Long, length: Int): ByteArray {
        val b = ByteArray(10)
        b[0] = 0
        b[1] = category.toByte()
        for (i in 0 until 4) {
            b[2 + i] = (address shr (8 * i)).toByte()
            b[6 + i] = (length shr (8 * i)).toByte()
        }
        return b
    }

    companion object {
        const val CAT_SESSION = 0x1C
        const val CAT_GROUP = 0x1D
        const val CAT_HEADER = 0x1E
        const val CAT_DATA = 0x1F
        const val CAT_LAPS = 0x20
        const val HEADERS_FROM = 4096L
        private const val KIND_START = 0x00
        private const val KIND_PARAMS = 0x04
        private const val KIND_SIZES = 0x06
        private const val KIND_MARK = 0x07
        private const val BULK_DATA: Byte = 0x05
        private const val SECTOR_END = 0x07
        private const val DROP_SECTOR = 0x06
        private const val ALL_SENT = 0x09
        private const val ABORTED = 0x03
        private const val BUSY_TRIES = 5
        private const val BUSY_RETRY_MS = 2_000L
        private const val MAX_BLOCKS = 2_048
    }
}
