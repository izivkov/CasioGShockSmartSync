package org.avmedia.gshockapi.io

/**
 * The watch face setting (request 0x51), as a frame: the code byte, then value index 0 = selected face,
 * index 1 to 4 = the four items of the user custom face (one item code each). Frame byte = index + 1.
 *
 * Pure: bytes in, bytes out. A change copies the frame and replaces only the byte it names.
 * Which slot of 1 to 4 is top right, and which face index is the custom face, are not known yet.
 */
object WatchFaceCodec {
    const val CODE = 0x51
    const val SLOTS = 4
    private const val FACE_AT = 1
    private const val FIRST_ITEM_AT = 2
    private const val MIN_FRAME = FIRST_ITEM_AT + SLOTS

    /** Face item names by code, 0 to 19. Code 0x0F has no recovered name. */
    val ITEM_NAMES: List<String> =
        listOf(
            "Steps", "Step goal graph", "Training status", "Monthly distance, run", "Monthly distance, walk",
            "Monthly distance, bike", "Floors", "Monthly graph, run", "Monthly graph, walk", "Monthly graph, bike",
            "Heart rate", "Highest/lowest heart rate", "Sleep score and level", "Sleep time", "Sleep rating",
            "Item 15", "Calories", "Active time", "Sunrise/sunset", "Moon age",
        )

    fun itemName(code: Int): String = ITEM_NAMES.getOrNull(code) ?: "Item $code"

    data class Face(
        val face: Int,
        /** Item codes of slots 1 to 4. */
        val items: List<Int>,
    )

    fun parse(frame: ByteArray): Face? {
        if (frame.size < MIN_FRAME || frame[0] != CODE.toByte()) return null
        return Face(frame[FACE_AT].toInt() and 0xFF, (0 until SLOTS).map { frame[FIRST_ITEM_AT + it].toInt() and 0xFF })
    }

    /** The frame with the selected face replaced; every other byte stays. */
    fun withFace(
        frame: ByteArray,
        face: Int,
    ): ByteArray? {
        if (parse(frame) == null || face !in 0..0xFF) return null
        return frame.copyOf().also { it[FACE_AT] = face.toByte() }
    }

    /** The frame with item [slot] (1 to 4) set to [item] (0 to 19); every other byte stays. */
    fun withItem(
        frame: ByteArray,
        slot: Int,
        item: Int,
    ): ByteArray? {
        if (parse(frame) == null || slot !in 1..SLOTS || item !in ITEM_NAMES.indices) return null
        return frame.copyOf().also { it[FIRST_ITEM_AT + slot - 1] = item.toByte() }
    }

    /** Frame bytes that [withFace] may change. */
    val FACE_BYTES: Set<Int> = setOf(FACE_AT)

    /** Frame bytes that [withItem] may change for [slot]. */
    fun itemBytes(slot: Int): Set<Int> = setOf(FIRST_ITEM_AT + slot - 1)
}
