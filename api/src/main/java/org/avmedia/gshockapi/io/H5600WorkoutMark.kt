package org.avmedia.gshockapi.io

/** The group header (1024 bytes, category 0x1D) and the "fetched" mark the app writes after each workout. */
object WorkoutGroup {
    const val SLOTS = 100
    const val SIZE = 1024
    private const val TRANSFERRED_FROM = 525
    const val MASK_BYTES = 13
    private const val BULK_MARK: Byte = 0x07

    /** Slots that hold a workout (bit cleared) in order. */
    fun usedSlots(header: ByteArray): List<Int> = slotsWithBitClear(header, 0)

    /** Slots already marked as transferred (bit cleared from byte 525). */
    fun transferredSlots(header: ByteArray): Set<Int> = slotsWithBitClear(header, TRANSFERRED_FROM).toSet()

    /** Used and not transferred: what to fetch. */
    fun pendingSlots(header: ByteArray): List<Int> {
        if (header.size < SIZE) return emptyList()
        val done = transferredSlots(header)
        return usedSlots(header).filter { it !in done }
    }

    private fun slotsWithBitClear(header: ByteArray, base: Int): List<Int> =
        (0 until SLOTS).filter { i ->
            val at = base + i / 8
            at < header.size && (header[at].toInt() shr (i % 8)) and 1 == 0
        }

    /**
     * Bulk `07` + flag + 13 bytes. Flag 0: more workouts follow, 1: none left. Bit i cleared = slot i transferred,
     * every other bit 1.
     */
    fun fetchedMark(transferred: Set<Int>, noneLeft: Boolean): ByteArray {
        val out = ByteArray(2 + MASK_BYTES)
        out[0] = BULK_MARK
        out[1] = if (noneLeft) 1 else 0
        for (b in 0 until MASK_BYTES) {
            var v = 0xFF
            for (bit in 0 until 8) if ((b * 8 + bit) in transferred) v = v or (1 shl bit)
            out[2 + b] = v.toByte()
        }
        return out
    }
}
