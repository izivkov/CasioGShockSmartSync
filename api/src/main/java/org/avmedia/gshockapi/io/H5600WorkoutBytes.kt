package org.avmedia.gshockapi.io

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Little-endian reader over a byte array for the workout layouts. Reads past the end throw; callers check sizes first. */
class WorkoutCursor(private val bytes: ByteArray, start: Int = 0) {
    var pos: Int = start
        private set

    val remaining: Int get() = bytes.size - pos

    fun skip(n: Int) { pos += n }

    fun u8(): Int = bytes[pos++].toInt() and 0xFF

    fun i8(): Int = bytes[pos++].toInt()

    fun u16(): Int = u8() or (u8() shl 8)

    fun i16(): Int = u16().toShort().toInt()

    fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)

    private fun buf(n: Int): ByteBuffer = ByteBuffer.wrap(bytes, pos, n).order(ByteOrder.LITTLE_ENDIAN).also { pos += n }

    fun f32(): Float = buf(4).float

    fun f64(): Double = buf(8).double

    fun rawBytes(n: Int): ByteArray = bytes.copyOfRange(pos, pos + n).also { pos += n }
}

/** Number coding of the workout layouts: BCD, "no data" marks and the CRC of a chunk. */
object WorkoutCodec {
    /** One BCD byte; null when a nibble is not a digit (also the all-ones "no data"). */
    fun bcd(b: Int): Int? {
        val hi = (b shr 4) and 0xF
        val lo = b and 0xF
        return if (hi > 9 || lo > 9) null else hi * 10 + lo
    }

    /** `hms`: three BCD bytes to seconds; null when any is no data. */
    fun hms(h: Int, m: Int, s: Int): Int? {
        val bh = bcd(h) ?: return null
        val bm = bcd(m) ?: return null
        val bs = bcd(s) ?: return null
        return bh * 3600 + bm * 60 + bs
    }

    /** `ms`: two BCD bytes, minutes then seconds, to seconds. */
    fun ms(m: Int, s: Int): Int? {
        val bm = bcd(m) ?: return null
        val bs = bcd(s) ?: return null
        return bm * 60 + bs
    }

    /** Unsigned value or null when it is all ones or all ones minus 1. */
    fun unsigned(v: Long, bits: Int): Long? {
        val max = (1L shl bits) - 1
        return if (v >= max - 1) null else v
    }

    /** Signed value or null when it is the highest positive value or that minus 1. */
    fun signed(v: Int, bits: Int): Int? {
        val max = (1 shl (bits - 1)) - 1
        return if (v >= max - 1) null else v
    }

    fun finite(f: Float): Double? = if (f.isNaN() || f.isInfinite()) null else f.toDouble()

    fun finite(d: Double): Double? = if (d.isNaN() || d.isInfinite()) null else d

    /** CRC-16/KERMIT: polynomial 0x8408 reflected, start 0, no final XOR. */
    fun crc16Kermit(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0x8409 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }
}
