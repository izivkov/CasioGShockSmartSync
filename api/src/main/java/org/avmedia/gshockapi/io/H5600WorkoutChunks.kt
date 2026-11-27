package org.avmedia.gshockapi.io

/**
 * Collects the data bytes of one workout read. Every chunk is `min(chunkSize, bytes left)` data bytes plus a 2 byte
 * CRC-16/KERMIT (low byte first) over the data as received; after the check the data bytes are inverted.
 * Chunks of the open sector are kept apart until [endSector] commits them, so a bad sector can be asked for again.
 */
class WorkoutChunkAssembler(private val total: Int, private val chunkSize: Int) {
    private var pending = ByteArray(0)
    private val committed = java.io.ByteArrayOutputStream()
    private val sector = java.io.ByteArrayOutputStream()
    private var committedBytes = 0
    private var sectorBytes = 0

    /** True once a chunk of the open sector failed its check. Cleared by [dropSector]. */
    var corrupt = false
        private set

    val receivedBytes: Int get() = committedBytes + sectorBytes

    val complete: Boolean get() = receivedBytes >= total && !corrupt

    /** Bytes of an unfinished chunk that are still waiting for the rest. */
    val partialBytes: Int get() = pending.size

    fun feed(data: ByteArray) {
        pending += data
        while (!corrupt) {
            val left = total - receivedBytes
            if (left <= 0) return
            val take = minOf(chunkSize, left)
            if (pending.size < take + CRC_BYTES) return
            val stored = (pending[take].toInt() and 0xFF) or ((pending[take + 1].toInt() and 0xFF) shl 8)
            if (WorkoutCodec.crc16Kermit(pending, 0, take) != stored) {
                corrupt = true
                return
            }
            for (i in 0 until take) sector.write(pending[i].toInt() xor 0xFF)
            sectorBytes += take
            pending = pending.copyOfRange(take + CRC_BYTES, pending.size)
        }
    }

    /** The sector is over: true and committed when it holds whole, good chunks only. */
    fun endSector(): Boolean {
        if (corrupt || pending.isNotEmpty()) return false
        committed.write(sector.toByteArray())
        committedBytes += sectorBytes
        sector.reset()
        sectorBytes = 0
        return true
    }

    /** Forgets the open sector (checksum failure, or the watch says it sends it again). */
    fun dropSector() {
        sector.reset()
        sectorBytes = 0
        pending = ByteArray(0)
        corrupt = false
    }

    /** All data, after a last [endSector]; null while bytes are missing. */
    fun result(): ByteArray? {
        if (sectorBytes > 0 && !endSector()) return null
        return if (committedBytes >= total) committed.toByteArray().copyOf(total) else null
    }

    companion object {
        const val CRC_BYTES = 2
        const val HEADER_CHUNK = 256
        const val BLOCK_CHUNK = 512

        /** One-shot form for a whole read received without sector marks; null on a bad chunk or missing bytes. */
        fun decode(
            raw: ByteArray,
            total: Int,
            chunkSize: Int,
        ): ByteArray? = WorkoutChunkAssembler(total, chunkSize).also { it.feed(raw) }.result()
    }
}
