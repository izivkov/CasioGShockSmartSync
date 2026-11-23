package org.avmedia.gshockapi.io

import java.time.LocalDate
import org.avmedia.gshockapi.model.CardioStatus

/**
 * The tagged groups of the current values section 0x65: each group is one tag byte and a fixed-size content.
 * Pure. On the recording of 2026-10-28 the body starts with group 0x5B, and the heart rate group 0x5E lands at body
 * offset 87, where [H5600HealthDecoder] reads the day's heart rate: the sizes below reproduce that layout.
 */
object H5600CurrentValues {
    const val TAG_CARDIO = 0x5B

    /** Content bytes after the tag. 0x61 is 24 times 2 BCD bytes plus one padding byte (recording 2026-10-28). */
    private val GROUP_BYTES = mapOf(0x5D to 4, 0x5C to 10, TAG_CARDIO to 9, 0x60 to 4, 0x62 to 21, 0x61 to 49, 0x5E to 6)

    /** Body offset of the content of each group, walked from the start; the walk stops at an unknown tag or a cut group. */
    fun groups(body: ByteArray): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        var at = 0
        while (at < body.size) {
            val tag = body[at].toInt() and 0xFF
            val size = GROUP_BYTES[tag] ?: break
            if (at + 1 + size > body.size) break
            out.putIfAbsent(tag, at + 1)
            at += 1 + size
        }
        return out
    }

    /** Today's cardio load from group 0x5B, dated [date] (the read's day); null without the group or without any value. */
    fun cardio(body: ByteArray?, date: LocalDate): CardioStatus? {
        if (body == null) return null
        val at = groups(body)[TAG_CARDIO] ?: return null
        return H5600BodyDecoder.cardioBlock(body, at, date)
    }
}
