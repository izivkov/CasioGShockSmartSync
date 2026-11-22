package org.avmedia.gshockapi.io

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds synthetic workout bytes from the layouts, never from a real recording. */
class Bytes {
    private val out = ByteArrayOutputStream()
    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun u16(v: Int) = apply { u8(v); u8(v shr 8) }
    fun u32(v: Long) = apply { u16(v.toInt() and 0xFFFF); u16((v shr 16).toInt() and 0xFFFF) }
    fun f(v: Float) = apply { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array()) }
    fun d(v: Double) = apply { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(v).array()) }
    fun bcd(v: Int) = apply { u8(((v / 10) shl 4) or (v % 10)) }
    fun hms(h: Int, m: Int, s: Int) = apply { bcd(h); bcd(m); bcd(s) }
    fun ms(m: Int, s: Int) = apply { bcd(m); bcd(s) }
    fun raw(b: ByteArray) = apply { out.write(b) }
    fun pad(n: Int, v: Int = 0) = apply { repeat(n) { u8(v) } }
    val size: Int get() = out.size()
    fun bytes(): ByteArray = out.toByteArray()
}

object WorkoutTestData {
    fun at(buf: ByteArray, pos: Int, part: ByteArray) = part.copyInto(buf, pos)

    /** Running summary, 87 bytes; with the float load of format 4: 91. */
    fun runningSummary(withLoadFloat: Boolean = true): ByteArray {
        val b = Bytes().u8(0x81).hms(0, 31, 5).f(5.25f).ms(5, 54).ms(5, 10).ms(7, 2)
            .u32(412).u8(60).u8(10).u8(30).u16(88).u8(3).u8(34).u8(1).u8(150).u8(178).u8(92)
            .u16(120).u16(95).u32(40).u32(38).u16(170).u16(182).u16(95).u16(7).u16(4)
            .pad(17).u8(30).u16(1990).u8(5).u8(14).u8(1).u16(180).u16(752).u8(0)
            .u8(2).u8(23).u8(0).u8(7).u8(0).u8(190).u8(52).u8(48)
        if (withLoadFloat) b.f(88.5f)
        return b.bytes()
    }

    fun header(
        sport: Int = 1, version: Int = 4, summary: ByteArray = runningSummary(),
        dataBytes: Long = 0, lapBytes: Long = 0, dataAddr: Long = 0, lapAddr: Long = 0,
    ): ByteArray {
        val h = ByteArray(256)
        h[0] = sport.toByte()
        h[1] = 0x02
        h[2] = version.toByte()
        at(h, 10, Bytes().u32(dataBytes).bytes())
        at(h, 18, Bytes().u32(lapBytes).bytes())
        at(h, 32, byteArrayOf(4))
        at(h, 33, Bytes().bcd(26).bcd(20).bcd(11).bcd(21).bcd(7).bcd(30).bcd(0).bytes())
        at(h, 40, Bytes().bcd(26).bcd(20).bcd(11).bcd(21).bcd(8).bcd(1).bcd(5).bytes())
        at(h, 47, Bytes().u32(dataAddr).u32(lapAddr).u32(0xFFFFFFFFL).bytes())
        at(h, 69, summary)
        return h
    }

    fun block(payload: ByteArray, next: Long = 0xFFFFFFFFL): ByteArray {
        val b = ByteArray(4096)
        at(b, 2, Bytes().u16(payload.size).bytes())
        at(b, 7, Bytes().u32(next).bytes())
        at(b, 16, payload)
        return b
    }

    fun runningLap(index: Int): ByteArray = Bytes().u8(0x21).u8(0xFE).u8(0).bcd(5 * (index + 1)).bcd(0).hms(0, 5, 0)
        .f(1.0f).ms(5, 0).u8(150).u8(170).u8(90).u16(3).u16(2).u16(172).u16(95).f(1.0f * (index + 1)).u8(0)
        .pad(2 * 2 * 5).u16(0x7FFF).pad(4).pad(4).bytes()

    fun runningSample(t: Int, hr: Int): ByteArray {
        val b = Bytes().u8(1).u8(0).bcd(t / 60).bcd(t % 60).u8(0).hms(0, 0, 0)
            .ms(5, 30).u8(hr).u8(3).u16(25).u16(170).d(52.5).d(4.9).f(t * 3f).u16(95)
            .f(0.5f).f(0.3f).f(0.2f).u32(1000).pad(34).u8(1)
        return b.bytes()
    }

    /** One chunk stream as the watch sends it: per chunk the inverted data plus the CRC of the inverted bytes. */
    fun chunked(data: ByteArray, chunk: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val n = minOf(chunk, data.size - pos)
            val inv = ByteArray(n) { (data[pos + it].toInt() xor 0xFF).toByte() }
            out.write(inv)
            val crc = WorkoutCodec.crc16Kermit(inv)
            out.write(crc and 0xFF)
            out.write(crc shr 8)
            pos += n
        }
        return out.toByteArray()
    }
}
