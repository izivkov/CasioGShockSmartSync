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

    /** Like [bulk], and true when the write result came (the next chunk waits for it). The default does not wait. */
    suspend fun bulkConfirmed(data: ByteArray): Boolean {
        bulk(data)
        return true
    }
}

class WorkoutMsg(val channel: H5600Channel, val data: ByteArray)

/** What one run did. [error] null = finished (also with nothing to fetch). */
class WorkoutRunResult(val downloaded: Int, val error: String?, val refused: Boolean = false)

/**
 * Steps 1 to 8 of the workout transfer on a [WorkoutLink]. The caller holds the exchange lock.
 * Each workout goes to [sink] first; only when the sink took it (true) is the fetched mark written for it, so a
 * workout that could not be stored stays on the watch for the next run. [capture] receives every message and the
 * decoded bytes of each read, for the raw dump. [log] gets one line per step (category, slot, result) for the connection log.
 */
class WorkoutTransfer(
    private val link: WorkoutLink,
    private val inbox: Channel<WorkoutMsg>,
    private val sink: suspend (WorkoutSession) -> Boolean,
    private val blockCache: MutableMap<String, ByteArray> = HashMap(),
    private val capture: (WorkoutMsg?, ByteArray?) -> Unit = { _, _ -> },
    private val replyTimeoutMs: Long = org.avmedia.gshockapi.casio.LongProcess.WORKOUT.answerMs!!,
    // The echo of `00 1C` came 6 to 10 s after the request (owner log 2026-11-23i), so the 5 s answer limit cannot hold for it;
    // it gets the 30 s limit of a read the user asked for, measured from our write.
    private val sessionTimeoutMs: Long = org.avmedia.gshockapi.casio.LongProcess.WORKOUT.totalMs!!,
    private val readTimeoutMs: Long = 10_000L,
    private val log: (String) -> Unit = {},
) {
    private class Fail(val text: String, val refused: Boolean = false) : Exception(text)

    /** The session request went out and no end (`04 1C`) or abort (`03 1C`) yet: a failed or cancelled run aborts it. */
    private var sessionOpen = false

    /** Step 3 began: the fast interval may be set, so an abort puts the slow one back first. */
    private var paramsSent = false

    suspend fun run(): WorkoutRunResult {
        var done = 0
        try {
            log("Workout read: session 1C requested")
            start()
            log("Workout read: 1C the watch is ready")
            val header = readGroup()
            val pending = WorkoutGroup.pendingSlots(header)
            val transferred = WorkoutGroup.transferredSlots(header).toMutableSet()
            log("Workout read: 1D group header read, ${WorkoutGroup.usedSlots(header).size} on the watch, ${pending.size} new (slots $pending)")
            val headers = pending.mapNotNull { slot -> readHeader(slot)?.let { slot to it } }
            if (headers.size < pending.size) throw Fail("a workout header could not be read")
            log("Workout read: 1E ${headers.size} workout headers read")
            val sorted = headers.sortedBy { it.second.startUtcMillis ?: Long.MAX_VALUE }
            for ((index, item) in sorted.withIndex()) {
                val (slot, hdr) = item
                val session = fetchWorkout(slot, hdr)
                if (!(runCatching { sink(session) }.getOrDefault(false))) throw Fail("the workout could not be stored; it stays on the watch")
                transferred += slot
                bulk("step 7", WorkoutGroup.fetchedMark(transferred, noneLeft = index == sorted.lastIndex))
                awaitBulk("step 7", KIND_MARK) ?: throw Fail("no answer to the fetched mark")
                log("Workout read: slot $slot stored and marked as fetched")
                blockCache.keys.removeAll { it.startsWith("${hdr.startUtcMillis}:") }
                done++
            }
            finish(abort = false)
            return WorkoutRunResult(done, null)
        } catch (f: Fail) {
            finish(abort = true)
            return WorkoutRunResult(done, f.text, f.refused)
        } finally {
            // Cancelled or thrown: the watch must not keep the session open for the next attempt.
            if (sessionOpen) close(abort = true)
        }
    }

    /**
     * Steps 1 to 3. The watch echoes the session request and answers the start state, in either order (owner log
     * 2026-11-23i); it answers `04` only after its echo, so step 3 starts when both came. A fail reason (kind `03`) ends at once.
     */
    private suspend fun start() {
        val open = request(CAT_SESSION, 0L, 0)
        sessionOpen = true
        drsp("step 1", open)
        var echoed = false
        var tries = 0
        val ok = withTimeoutOrNull(sessionTimeoutMs) {
            while (true) {
                bulk("step 2", byteArrayOf(0, 0, 0))
                val reply = withTimeoutOrNull(replyTimeoutMs) {
                    var got: ByteArray? = null
                    while (got == null) {
                        val m = take()
                        if (m.data.contentEquals(open)) {
                            echoed = true
                            log("Workout read: step 1 rx ${hex(m.data)} (session echo)")
                        } else {
                            failReason(m.data)
                            if (m.channel == H5600Channel.CONVOY && m.data.firstOrNull()?.toInt() == KIND_START) got = m.data
                        }
                    }
                    got
                } ?: throw Fail("the watch did not answer the start")
                log("Workout read: step 2 rx ${hex(reply)} (start state)")
                val state = reply.getOrElse(1) { 0 }.toInt() and 0xFF
                val reason = reply.getOrElse(2) { 0 }.toInt() and 0xFF
                if (state == 0) break
                when (reason) {
                    1 -> throw Fail("the watch battery is too low for the workout transfer", refused = true)
                    2 -> throw Fail("the watch is too cold for the workout transfer", refused = true)
                    else -> if (++tries >= BUSY_TRIES) throw Fail("the watch stayed busy (reason $reason)", refused = true) else delay(BUSY_RETRY_MS)
                }
            }
            while (!echoed) {
                val m = take()
                failReason(m.data)
                if (m.data.contentEquals(open)) { echoed = true; log("Workout read: step 1 rx ${hex(m.data)} (session echo)") }
            }
            true
        }
        if (ok == null) throw Fail(if (echoed) "the watch did not answer the start" else "the watch did not open the session (no 00 1C echo in ${sessionTimeoutMs / 1000} s)")
        paramsSent = true
        bulk("step 3", ByteArray(10).also { it[0] = 4 })
        awaitBulk("step 3", KIND_PARAMS) ?: throw Fail("no answer to the connection parameters")
        bulk("step 3", byteArrayOf(4, 1, 0x18, 0, 0x18, 0, 0, 0, 0xDC.toByte(), 5))
        awaitBulk("step 3", KIND_PARAMS) ?: throw Fail("no answer to the interval")
        bulk("step 3", ByteArray(14).also { it[0] = 6 })
        val parameters = awaitBulk("step 3", KIND_SIZES) ?: throw Fail("no answer to the transfer parameters")
        bulk("step 3", parameters)
        awaitBulk("step 3", KIND_SIZES)
    }

    /** Kind `03` (fail reason, A1) on either channel ends the start: owner log 21:57:55 `03 01`. */
    private fun failReason(data: ByteArray) {
        if (data.firstOrNull()?.toInt() == ABORTED && data.size >= 2) {
            log("Workout read: rx ${hex(data)} (fail reason)")
            throw Fail("the watch refused the transfer (fail reason ${data[1].toInt() and 0xFF})")
        }
    }

    private fun drsp(step: String, data: ByteArray) {
        log("Workout read: $step tx ${hex(data)}")
        link.drsp(data)
    }

    private fun bulk(step: String, data: ByteArray) {
        log("Workout read: $step tx bulk ${hex(data)}")
        link.bulk(data)
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
        log("Workout read: slot $slot 1F ${data.size} data blocks")
        val laps = chain(h, CAT_LAPS, h.lapAddress, h.lapBytes) ?: throw Fail("workout in slot $slot: lap blocks could not be read")
        // The one-minute walking laps (header offset 55) are read to keep the chain complete; they are not decoded yet.
        val minute = chain(h, CAT_LAPS, h.minuteLapAddress, h.minuteLapBytes) ?: throw Fail("workout in slot $slot: minute lap blocks could not be read")
        log("Workout read: slot $slot 20 ${laps.size} lap blocks, ${minute.size} minute lap blocks")
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
            val step = "read %02X".format(category)
            drsp(step, request(category, address, length))
            var bad = 0
            while (true) {
                val m = next(readTimeoutMs) ?: return null.also { log("Workout read: $step rx nothing in ${readTimeoutMs / 1000} s (${asm.receivedBytes} of $length bytes)") }
                if (m.channel == H5600Channel.CONVOY) {
                    if (m.data.size > 1 && m.data[0] == BULK_DATA) asm.feed(m.data.copyOfRange(1, m.data.size))
                    continue
                }
                when (m.data.firstOrNull()?.toInt()) {
                    SECTOR_END ->
                        if (asm.endSector()) {
                            drsp(step, byteArrayOf(SECTOR_END.toByte(), category.toByte()))
                        } else {
                            if (++bad >= 2) return null
                            asm.dropSector()
                            drsp(step, byteArrayOf(0x08, category.toByte()))
                            drsp(step, byteArrayOf(0x06, category.toByte()))
                        }
                    DROP_SECTOR -> asm.dropSector()
                    ALL_SENT -> {
                        drsp(step, byteArrayOf(ALL_SENT.toByte(), category.toByte()))
                        drsp(step, byteArrayOf(0x04, category.toByte()))
                        val result = asm.result()
                        if (result != null) {
                            capture(null, result)
                            return result
                        }
                        log("Workout read: $step incomplete (${asm.receivedBytes} of $length bytes, ${asm.partialBytes} in an open chunk)")
                        drsp(step, byteArrayOf(0x03, category.toByte()))
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

    /** The next frame without a limit of its own; the caller's limit counts from its write. */
    private suspend fun take(): WorkoutMsg = inbox.receive().also { capture(it, null) }

    /** The answer of [kind] within [replyTimeoutMs] of our write (other frames in between do not stretch it), logged with its bytes. */
    private suspend fun awaitBulk(step: String, kind: Int): ByteArray? =
        withTimeoutOrNull(replyTimeoutMs) {
            var got: ByteArray? = null
            while (got == null) {
                val m = take()
                if (m.channel == H5600Channel.CONVOY && m.data.firstOrNull()?.toInt() == kind) got = m.data
            }
            got
        }.also { log("Workout read: $step rx " + (it?.let { b -> "bulk ${hex(b)}" } ?: "nothing in ${replyTimeoutMs / 1000} s")) }

    /** Step 8. Before step 3 nothing was set, so an abort then only sends `03 1C`. */
    private suspend fun finish(abort: Boolean) {
        if (paramsSent) {
            bulk("step 8", ByteArray(10).also { it[0] = 4 })
            awaitBulk("step 8", KIND_PARAMS)
            bulk("step 8", byteArrayOf(4, 1, 0x30, 1, 0x40, 1, 4, 0, 0xDC.toByte(), 5))
            awaitBulk("step 8", KIND_PARAMS)
        }
        close(abort)
    }

    /** Abort inside the block categories names the session category. */
    private fun close(abort: Boolean) {
        sessionOpen = false
        drsp("step 8", byteArrayOf(if (abort) 0x03 else 0x04, CAT_SESSION.toByte()))
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

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
