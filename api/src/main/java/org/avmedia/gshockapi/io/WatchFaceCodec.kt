package org.avmedia.gshockapi.io

/**
 * The watch face setting (request 0x51), as a frame: the code byte, then value index 0 = selected face,
 * index 1 to 4 = the four items of the user custom face (one item code each). Frame byte = index + 1.
 *
 * Pure: bytes in, bytes out. A change copies the frame and replaces only the byte it names.
 * Owner's test on the watch: face 0 does nothing; faces 1 to 4 each change the face, and face 2 is the custom face.
 * On the custom face only slot 3 changes anything (the top-right item); slots 1, 2 and 4 are never written.
 */
object WatchFaceCodec {
    const val CODE = 0x51
    const val SLOTS = 4
    /** The faces the watch has: 1 to 4. Face 0 does nothing and is refused. */
    val FACES: IntRange = 1..4

    /** The custom face, and the slot of it that does something: the top-right item. */
    const val CUSTOM_FACE = 2
    const val TOP_RIGHT_SLOT = 3

    fun faceName(face: Int): String =
        when (face) {
            1 -> "Time only"
            CUSTOM_FACE -> "Custom"
            3 -> "Steps"
            4 -> "Tide" // the owner was not fully sure of this one
            else -> "Face $face"
        }

    private const val FACE_AT = 1
    private const val FIRST_ITEM_AT = 2
    private const val MIN_FRAME = FIRST_ITEM_AT + SLOTS

    /** Face item names by code, 0 to 19, as in the notes. */
    val ITEM_NAMES: List<String> =
        listOf(
            "Steps", "Step goal graph", "Training status", "Monthly distance run", "Monthly distance walk",
            "Monthly distance bike", "Floors", "Monthly graph run", "Monthly graph walk", "Monthly graph bike",
            "Heart rate", "Highest/lowest heart rate", "Sleep score and level", "Sleep time", "Sleep rating",
            "Stress score", "Calories", "Active time", "Sunrise/sunset", "Moon age",
        )

    /** The stress score (0x0F) is in the code table but not among the items the watch offers. */
    const val STRESS_ITEM = 0x0F

    /** The item codes a person can choose: all but [STRESS_ITEM]. */
    val OFFERED_ITEMS: List<Int> = ITEM_NAMES.indices.filter { it != STRESS_ITEM }

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

    /** The frame with the selected face replaced (faces 1 to 4 only; anything else is refused); every other byte stays. */
    fun withFace(
        frame: ByteArray,
        face: Int,
    ): ByteArray? = if (face in FACES) withFaceRaw(frame, face) else null

    /** Any face index 0 to 255, for decoding only (the Details part of the screen). */
    fun withFaceRaw(
        frame: ByteArray,
        face: Int,
    ): ByteArray? {
        if (parse(frame) == null || face !in 0..0xFF) return null
        return frame.copyOf().also { it[FACE_AT] = face.toByte() }
    }

    /** The top-right item of the custom face: slot 3 only, every other byte stays. */
    fun withTopRight(
        frame: ByteArray,
        item: Int,
    ): ByteArray? = if (item in OFFERED_ITEMS) withItem(frame, TOP_RIGHT_SLOT, item) else null

    /** Frame bytes that [withTopRight] may change. */
    val TOP_RIGHT_BYTES: Set<Int> get() = itemBytes(TOP_RIGHT_SLOT)

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
