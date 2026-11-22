package org.avmedia.gshockapi.io

import java.time.LocalDateTime
import java.time.ZoneOffset

/** The 256 byte workout header (notes H2). */
class WorkoutHeader(
    val sport: Int,
    val flags: Int,
    val formatVersion: Int,
    val dataBytes: Long,
    val lapBytes: Long,
    val minuteLapBytes: Long,
    val utcOffsetRaw: Int,
    val startUtcMillis: Long?,
    val endUtcMillis: Long?,
    val dataAddress: Long,
    val lapAddress: Long,
    val minuteLapAddress: Long,
    val raw: ByteArray,
) {
    val hasGps: Boolean get() = flags and 0x02 != 0

    companion object {
        const val SIZE = 256
        const val NO_ADDRESS = 0xFFFFFFFFL

        /** Null when [bytes] is shorter than a header. */
        fun decode(bytes: ByteArray): WorkoutHeader? {
            if (bytes.size < SIZE) return null
            fun u32At(pos: Int) = WorkoutCursor(bytes, pos).u32()
            val c = WorkoutCursor(bytes)
            val sport = c.u8()
            val flags = c.u8()
            val version = c.u16() and 0x7FFF
            val data = u32At(10)
            val laps = u32At(18)
            val minute = u32At(26)
            val offset = WorkoutCursor(bytes, 32).i8()
            val tc = WorkoutCursor(bytes, 33)
            val start = time(tc)
            val end = time(tc)
            val a1 = u32At(47)
            val a2 = u32At(51)
            val a3 = u32At(55)
            return WorkoutHeader(sport, flags, version, data, laps, minute, offset, start, end, a1, a2, a3, bytes.copyOf(SIZE))
        }

        /** Year (2 BCD, low pair first), month, day, hour, minute, second (BCD) to UTC epoch millis. */
        private fun time(c: WorkoutCursor): Long? {
            val yLo = WorkoutCodec.bcd(c.u8())
            val yLo = WorkoutCodec.bcd(c.u8())
            val parts = List(5) { WorkoutCodec.bcd(c.u8()) }
            if (yLo == null || yHi == null || parts.any { it == null }) return null
            return runCatching {
                LocalDateTime.of(yHi * 100 + yLo, parts[0]!!, parts[1]!!, parts[2]!!, parts[3]!!, parts[4]!!)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull()
        }
    }
}
