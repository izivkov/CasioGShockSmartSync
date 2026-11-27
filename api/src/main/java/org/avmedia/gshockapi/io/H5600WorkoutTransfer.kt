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
class WorkoutRunResult(val downloaded: Int, val error: String?, val refused: Boolean = false, val failReason: Int? = null)

/**
 * Steps 1 to 8 of the workout transfer on a [WorkoutLink]. The caller holds the exchange lock.
 * Each workout goes to [sink] first; only when the sink took it (true) is the fetched mark written for it, so a
 * workout that could not be stored stays on the watch for the next run. [capture] receives every message and the
 * decoded bytes of each read (a test seam; the frames themselves are App log lines). [log] gets one line per step (category, slot, result) for the connection log.
 */
class WorkoutTransfer(
    private val link: WorkoutLink,
    private val inbox: Channel<WorkoutMsg>,
    private val sink: suspend (WorkoutSession) -> Boolean,
    private val blockCache: MutableMap<String, ByteArray> = HashMap(),
    private val capture: (WorkoutMsg?, ByteArray?) -> Unit = { _, _ -> },
    private val replyTimeoutMs: Long = org.avmedia.gshockapi.casio.LongProcess.WORKOUT.answerMs!!,
    // The echo of `00 1C` came 6 to 10 s after the request, so the 5 s answer limit cannot hold for it;
    // it gets the 30 s limit of a read the user asked for, measured from our write.
    private val sessionTimeoutMs: Long = org.avmedia.gshockapi.casio.LongProcess.WORKOUT.totalMs!!,
    private val readTimeoutMs: Long = 10_000L,
    private val log: (String) -> Unit = {},
) {
    private class Fail(val text: String, val refused: Boolean = false, val failReason: Int? = null) : Exception(text)

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
            log(
                "Workout read: 1D group header read, ${WorkoutGroup.usedSlots(
                    header,
                ).size} on the watch, ${pending.size} new (slots $pending)",
            )
            val headers = pending.mapNotNull { slot -> readHeader(slot)?.let { slot to it } }
            if (headers.size < pending.size) throw Fail("a workout header could not be read")
            log("Workout read: 1E ${headers.size} workout headers read")
            val sorted = headers.sortedBy { it.second.startUtcMillis ?: Long.MAX_VALUE }
            for ((index, item) in sorted.withIndex()) {
                val (slot, hdr) = item
                val session = fetchWorkout(slot, hdr)
                if (!(
                        runCatching {
                            sink(
                                session,
                            )
                        }.getOrDefault(false)
                    )
                ) {
                    throw Fail("the workout could not be stored; it stays on the watch")
                }
                transferred += slot
                bulk("step 7", WorkoutGroup.fetchedMark(transferred, noneLeft = index == sorted.lastIndex))
                awaitBulk("step 7", KIND_MARK) ?: throw Fail("no answer to the fetched mark")
                log("Workout read: slot $slot stored and marked as fetched")
                // A request 12 ms after the mark answer was not taken (the watch replayed its last read): give it time.
                if (index < sorted.lastIndex) delay(AFTER_MARK_MS)
                blockCache.keys.removeAll { it.startsWith("${hdr.startUtcMillis}:") }
                done++
            }
            finish(abort = false)
            return WorkoutRunResult(done, null)
        } catch (f: Fail) {
            finish(abort = true)
            return WorkoutRunResult(done, f.text, f.refused, f.failReason)
        } finally {
            // Cancelled or thrown: the watch must not keep the session open for the next attempt.
            if (sessionOpen) close(abort = true)
        }
    }

    /**
     * Steps 1 to 3. The watch echoes the session request and answers the start state, in either
     * order; it answers `04` only after its echo, so step 3 starts when both came. A fail reason (kind `03`) ends at once.
     */
    private suspend fun start() {
        val st = StartState(request(CAT_SESSION, 0L, 0))
        sessionOpen = true
        drsp("step 1", st.open)
        val ok =
            withTimeoutOrNull(sessionTimeoutMs + BUSY_TRIES * BUSY_RETRY_MS) {
                awaitReady(st)
                while (!st.echoed) sawEcho(take(), st)
                true
            }
        if (ok == null) {
            throw Fail(
                if (st.echoed) "the watch did not answer the start" else "the watch did not open the session (no 00 1C echo in ${sessionTimeoutMs / 1000} s)",
            )
        }
        paramsSent = true
        sendParameters()
    }

    /** The session request and whether its echo came. */
    private class StartState(val open: ByteArray) {
        var echoed = false
    }

    /** Step 2 until the watch answers state 0; a busy watch is asked again, a low battery or cold ends it. */
    private suspend fun awaitReady(st: StartState) {
        var tries = 0
        while (true) {
            dropQueued(st)
            bulk("step 2", byteArrayOf(0, 0, 0))
            val reply = awaitStartState(st)
            log("Workout read: step 2 rx ${hex(reply)} (start state)")
            val state = reply.getOrElse(1) { 0 }.toInt() and 0xFF
            val reason = reply.getOrElse(2) { 0 }.toInt() and 0xFF
            if (state == 0) return
            tries = busyOrRefused(reason, tries)
        }
    }

    /**
     * One answer per write: a busy watch repeats `00 01 04` about every 60 ms; the frames queued since the last
     * write are dropped (the session echo and a fail reason among them still count).
     */
    private fun dropQueued(st: StartState) {
        while (true) {
            val m = inbox.tryReceive().getOrNull() ?: break
            capture(m, null)
            sawEcho(m, st)
        }
    }

    /** The start state (kind `00` on the bulk channel) within [replyTimeoutMs]. */
    private suspend fun awaitStartState(st: StartState): ByteArray =
        withTimeoutOrNull(replyTimeoutMs) {
            var got: ByteArray? = null
            while (got == null) {
                val m = take()
                if (!sawEcho(m, st) && m.channel == H5600Channel.CONVOY && m.data.firstOrNull()?.toInt() == KIND_START) got = m.data
            }
            got
        } ?: throw Fail("the watch did not answer the start")

    /** Reason 1 or 2 ends the start; another reason waits and counts one more try (the new count). */
    private suspend fun busyOrRefused(
        reason: Int,
        tries: Int,
    ): Int {
        when (reason) {
            1 -> throw Fail("the watch battery is too low for the workout transfer", refused = true)
            2 -> throw Fail("the watch is too cold for the workout transfer", refused = true)
        }
        if (tries + 1 >= BUSY_TRIES) throw Fail("the watch stayed busy (reason $reason)", refused = true)
        delay(BUSY_RETRY_MS)
        return tries + 1
    }

    /** True for the session echo (noted and logged); any other frame is checked for a fail reason. */
    private fun sawEcho(
        m: WorkoutMsg,
        st: StartState,
    ): Boolean {
        if (m.data.contentEquals(st.open)) {
            st.echoed = true
            log("Workout read: step 1 rx ${hex(m.data)} (session echo)")
            return true
        }
        failReason(m.data)
        return false
    }

    /** Step 3: connection parameters, the fast interval, the transfer parameters written back. */
    private suspend fun sendParameters() {
        bulk(STEP_3, ByteArray(10).also { it[0] = 4 })
        awaitBulk(STEP_3, KIND_PARAMS) ?: throw Fail("no answer to the connection parameters")
        bulk(STEP_3, byteArrayOf(4, 1, 0x18, 0, 0x18, 0, 0, 0, 0xDC.toByte(), 5))
        awaitBulk(STEP_3, KIND_PARAMS) ?: throw Fail("no answer to the interval")
        bulk(STEP_3, ByteArray(14).also { it[0] = 6 })
        val parameters = awaitBulk(STEP_3, KIND_SIZES) ?: throw Fail("no answer to the transfer parameters")
        bulk(STEP_3, parameters)
        awaitBulk(STEP_3, KIND_SIZES)
    }

    /** Kind `03` (fail reason, A1) on either channel ends the start, e.g. `03 01`. */
    private fun failReason(data: ByteArray) {
        if (data.firstOrNull()?.toInt() == ABORTED && data.size >= 2) {
            log("Workout read: rx ${hex(data)} (fail reason)")
            throw Fail("the watch refused the transfer (fail reason ${data[1].toInt() and 0xFF})", failReason = data[1].toInt() and 0xFF)
        }
    }

    private fun drsp(
        step: String,
        data: ByteArray,
    ) {
        log("Workout read: $step tx ${hex(data)}")
        link.drsp(data)
    }

    private fun bulk(
        step: String,
        data: ByteArray,
    ) {
        log("Workout read: $step tx bulk ${hex(data)}")
        link.bulk(data)
    }

    private suspend fun readGroup(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until 4) out.write(
            read(CAT_GROUP, i * 256L, 256, WorkoutChunkAssembler.HEADER_CHUNK) ?: throw Fail("the group header could not be read"),
        )
        return out.toByteArray()
    }

    private suspend fun readHeader(slot: Int): WorkoutHeader? =
        read(CAT_HEADER, HEADERS_FROM + 256L * slot, 256, WorkoutChunkAssembler.HEADER_CHUNK)?.let { WorkoutHeader.decode(it) }

    private suspend fun fetchWorkout(
        slot: Int,
        h: WorkoutHeader,
    ): WorkoutSession {
        val data = chain(h, CAT_DATA, h.dataAddress, h.dataBytes) ?: throw Fail("workout in slot $slot: data blocks could not be read")
        log("Workout read: slot $slot 1F ${data.size} data blocks")
        val laps = chain(h, CAT_LAPS, h.lapAddress, h.lapBytes) ?: throw Fail("workout in slot $slot: lap blocks could not be read")
        // The one-minute walking laps (header offset 55) are read to keep the chain complete; they are not decoded yet.
        val minute = chain(h, CAT_LAPS, h.minuteLapAddress, h.minuteLapBytes) ?: throw Fail("workout in slot $slot: minute lap blocks could not be read")
        log("Workout read: slot $slot 20 ${laps.size} lap blocks, ${minute.size} minute lap blocks")
        return WorkoutSessionBuilder.build(slot, h, data, laps)
    }

    /**
     * Reads a block chain. Blocks already read for the same workout (same start time) are not asked for again.
     * Inside the chain no `04 cat` goes between blocks; after its last block read the chain is closed with `04 cat`.
     * Left open, the next request of another category got a replay of the last block read (2026-11-24, `00 20` after
     * the 1F chain, three runs); after a closed read (`04 1E`) the same `00 20` was served.
     */
    private suspend fun chain(
        h: WorkoutHeader,
        category: Int,
        first: Long,
        announced: Long,
    ): List<ByteArray>? {
        if (announced == 0L || first == WorkoutHeader.NO_ADDRESS) return emptyList()
        val blocks = ArrayList<ByteArray>()
        val seen = HashSet<Long>()
        var asked = false
        var addr: Long? = first
        while (addr != null && seen.add(addr) && blocks.size < MAX_BLOCKS) {
            val key = "${h.startUtcMillis}:$category:$addr"
            val block =
                blockCache[key] ?: (read(category, addr, WorkoutRecords.BLOCK_SIZE, WorkoutChunkAssembler.BLOCK_CHUNK) ?: return null).also {
                    asked = true
                }
            blockCache[key] = block
            blocks += block
            addr = WorkoutRecords.nextAddress(block)
        }
        if (asked) drsp("read %02X".format(category), byteArrayOf(0x04, category.toByte()))
        return blocks
    }

    /**
     * One read (`00 cat address length`) with sector answers, one retry of a sector and one retry of the whole read.
     * An echo of another request is the watch replaying its last read: its bytes are dropped, its end is answered and
     * our request goes out again (once per read).
     */
    private suspend fun read(
        category: Int,
        address: Long,
        length: Int,
        chunk: Int,
    ): ByteArray? {
        val r = ReadCall(category, length, chunk, request(category, address, length))
        repeat(2) {
            r.restart()
            if (category == CAT_DATA || category == CAT_LAPS) delay(BLOCK_GAP_MS)
            drsp(r.step, r.ours)
            val end = readAttempt(r)
            if (end is ReadStep.End) return end.bytes
        }
        return null
    }

    /** One read in progress: [resent] counts over both attempts, the rest is new for each attempt. */
    private class ReadCall(val category: Int, val length: Int, val chunk: Int, val ours: ByteArray) {
        val step = "read %02X".format(category)
        var resent = false
        var asm = WorkoutChunkAssembler(length, chunk)
        var stale: ByteArray? = null
        var bad = 0

        fun restart() {
            asm = WorkoutChunkAssembler(length, chunk)
            stale = null
            bad = 0
        }
    }

    /** What a frame means for the read: wait for the next, ask the whole read again, or the read ends ([End.bytes]). */
    private sealed class ReadStep {
        object Next : ReadStep()

        object Again : ReadStep()

        class End(val bytes: ByteArray?) : ReadStep()
    }

    /** Frames until the read ends or asks again ([ReadStep.Again]); nothing within [readTimeoutMs] ends it. */
    private suspend fun readAttempt(r: ReadCall): ReadStep {
        while (true) {
            val m =
                next(readTimeoutMs) ?: return ReadStep.End(null).also {
                    log("Workout read: ${r.step} rx nothing in ${readTimeoutMs / 1000} s (${r.asm.receivedBytes} of ${r.length} bytes)")
                }
            val step = onReadFrame(r, m)
            if (step !is ReadStep.Next) return step
        }
    }

    private suspend fun onReadFrame(
        r: ReadCall,
        m: WorkoutMsg,
    ): ReadStep {
        if (m.channel == H5600Channel.CONVOY) {
            if (r.stale == null && m.data.size > 1 && m.data[0] == BULK_DATA) r.asm.feed(m.data.copyOfRange(1, m.data.size))
            return ReadStep.Next
        }
        if (isOtherEcho(m.data, r.ours)) {
            r.stale = m.data
            return ReadStep.Next
        }
        val kind = m.data.firstOrNull()?.toInt()
        val replay = r.stale
        if (replay != null && (kind == SECTOR_END || kind == ALL_SENT)) return answerReplay(r, m.data, replay)
        // The watch's `07` and `09` frames carry more than the category (`07 1F 00 00 00 00 00`); like the LifeLog
        // block confirm, the whole frame goes back. A short `07 1F` got no answer at all: the watch waited for the echo.
        return when (kind) {
            SECTOR_END -> onSectorEnd(r, m.data)
            DROP_SECTOR -> {
                r.asm.dropSector()
                ReadStep.Next
            }
            ALL_SENT -> onAllSent(r, m.data)
            ABORTED -> ReadStep.End(null)
            else -> ReadStep.Next
        }
    }

    /** The end of a replayed read: answered, its bytes dropped, and our request sent again (once per read). */
    private suspend fun answerReplay(
        r: ReadCall,
        frame: ByteArray,
        replay: ByteArray,
    ): ReadStep {
        drsp(r.step, echo(frame, replay[1].toInt() and 0xFF))
        if (r.resent) return ReadStep.End(null).also { log("Workout read: ${r.step} stale replay again, read given up") }
        r.resent = true
        r.stale = null
        r.asm = WorkoutChunkAssembler(r.length, r.chunk)
        log("Workout read: stale replay of ${describe(replay)}, request sent again")
        delay(BLOCK_GAP_MS)
        drsp(r.step, r.ours)
        return ReadStep.Next
    }

    private fun onSectorEnd(
        r: ReadCall,
        frame: ByteArray,
    ): ReadStep {
        if (r.asm.endSector()) {
            drsp(r.step, echo(frame, r.category))
            // The sector end with every asked byte in is the end of a block read (1F, 20): the watch then
            // waits for the next request and sends no `09`; [chain] closes the chain. 1D and 1E end with `09`.
            return r.asm.result()?.let {
                capture(null, it)
                ReadStep.End(it)
            } ?: ReadStep.Next
        }
        if (++r.bad >= 2) return ReadStep.End(null)
        r.asm.dropSector()
        drsp(r.step, byteArrayOf(0x08, r.category.toByte()))
        drsp(r.step, byteArrayOf(0x06, r.category.toByte()))
        return ReadStep.Next
    }

    /** `09`: answered and closed; an incomplete read is aborted and asked again. */
    private fun onAllSent(
        r: ReadCall,
        frame: ByteArray,
    ): ReadStep {
        drsp(r.step, echo(frame, r.category))
        drsp(r.step, byteArrayOf(0x04, r.category.toByte()))
        val result = r.asm.result()
        if (result != null) {
            capture(null, result)
            return ReadStep.End(result)
        }
        log("Workout read: ${r.step} incomplete (${r.asm.receivedBytes} of ${r.length} bytes, ${r.asm.partialBytes} in an open chunk)")
        drsp(r.step, byteArrayOf(0x03, r.category.toByte()))
        return ReadStep.Again
    }

    private suspend fun next(timeoutMs: Long): WorkoutMsg? = withTimeoutOrNull(timeoutMs) { inbox.receive() }?.also { capture(it, null) }

    /** The next frame without a limit of its own; the caller's limit counts from its write. */
    private suspend fun take(): WorkoutMsg = inbox.receive().also { capture(it, null) }

    /** The answer of [kind] within [replyTimeoutMs] of our write (other frames in between do not stretch it), logged with its bytes. */
    private suspend fun awaitBulk(
        step: String,
        kind: Int,
    ): ByteArray? =
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
            bulk(STEP_8, ByteArray(10).also { it[0] = 4 })
            awaitBulk(STEP_8, KIND_PARAMS)
            bulk(STEP_8, byteArrayOf(4, 1, 0x30, 1, 0x40, 1, 4, 0, 0xDC.toByte(), 5))
            awaitBulk(STEP_8, KIND_PARAMS)
        }
        close(abort)
    }

    /** Abort inside the block categories names the session category. */
    private fun close(abort: Boolean) {
        sessionOpen = false
        drsp(STEP_8, byteArrayOf(if (abort) 0x03 else 0x04, CAT_SESSION.toByte()))
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }

    /** A read request echo (`00 cat address length`) of a block category that is not [ours]. */
    private fun isOtherEcho(
        frame: ByteArray,
        ours: ByteArray,
    ): Boolean =
        frame.size == ours.size && frame[0].toInt() == 0 && (frame[1].toInt() and 0xFF) in CAT_GROUP..CAT_LAPS && !frame.contentEquals(ours)

    /** `cat address` of a request echo, both hex, e.g. `20 00016000`. */
    private fun describe(echo: ByteArray): String =
        "%02X %08X".format(echo[1].toInt() and 0xFF, (0 until 4).sumOf { (echo[2 + it].toLong() and 0xFF) shl (8 * it) })

    /** The answer to a `07` or `09` frame of the watch: the frame itself; a bare one-byte frame gets its category added. */
    private fun echo(
        frame: ByteArray,
        category: Int,
    ): ByteArray = if (frame.size >= 2) frame.copyOf() else byteArrayOf(frame[0], category.toByte())

    private fun request(
        category: Int,
        address: Long,
        length: Int,
    ): ByteArray {
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
        private const val STEP_3 = "step 3"
        private const val STEP_8 = "step 8"

        /** Busy (state not 0, reason other than 1 or 2): tried again every [BUSY_RETRY_MS] for about 30 s. */
        private const val BUSY_TRIES = 15
        private const val BUSY_RETRY_MS = 2_000L
        private const val MAX_BLOCKS = 2_048

        /** Pause after a fetched mark answer before the next read request (12 ms was too soon, see [run]). */
        private const val AFTER_MARK_MS = 1_000L

        /**
         * Pause before a block request (1F, 20) and before a request sent again after a stale replay. Requests 10 to 33 ms
         * after the previous sector end were not taken and the last read was replayed; 300 ms costs about 6 s on 20 blocks.
         */
        private const val BLOCK_GAP_MS = 300L
    }
}
