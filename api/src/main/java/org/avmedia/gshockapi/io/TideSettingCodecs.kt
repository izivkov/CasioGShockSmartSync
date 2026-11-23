package org.avmedia.gshockapi.io

/**
 * Request 0x2E, unit 2: which tide data the watch shows. Frame = `2E 02` + value; value index i is frame byte 2 + i.
 * Pure: a change copies the frame and replaces only the bytes it names. A longer value than the 6 bytes known is kept unchanged.
 */
object TideSettingCodec {
    const val CODE = 0x2E
    const val UNIT = 2
    private const val VALUE_AT = 2
    private const val MIN_VALUE = 6

    /** Index 0: 0 preset port of the watch, 1 to 3 own point, 4 the port sent by the app. */
    const val SOURCE_PRESET = 0
    const val SOURCE_APP_PORT = 4

    data class Setting(val source: Int, val presetPort: Int, val appPort: Int, val feet: Boolean)

    fun request(): ByteArray = byteArrayOf(CODE.toByte(), UNIT.toByte())

    fun parse(frame: ByteArray): Setting? {
        if (frame.size < VALUE_AT + MIN_VALUE || frame[0] != CODE.toByte() || frame[1] != UNIT.toByte()) return null
        fun v(i: Int) = frame[VALUE_AT + i].toInt() and 0xFF
        return Setting(v(0), v(1) or (v(2) shl 8), v(3) or (v(4) shl 8), v(5) and 1 == 1)
    }

    fun sourceName(source: Int): String =
        when (source) {
            0 -> "Preset port of the watch"
            1, 2, 3 -> "Own point $source"
            4 -> "Port sent by the app"
            else -> "Unknown ($source)"
        }

    /** Shown data: 0 to 4 only; anything else is refused. */
    fun withSource(frame: ByteArray, source: Int): ByteArray? =
        if (parse(frame) == null || source !in 0..4) null else frame.copyOf().also { it[VALUE_AT] = source.toByte() }

    /** The port id of the port sent by the app (index 3 and 4, low byte first). */
    fun withAppPort(frame: ByteArray, id: Int): ByteArray? {
        if (parse(frame) == null || id !in 0..0xFFFF) return null
        return frame.copyOf().also {
            it[VALUE_AT + 3] = id.toByte()
            it[VALUE_AT + 4] = (id shr 8).toByte()
        }
    }

    /** Heights in feet (index 5 bit 0); the other bits stay. */
    fun withFeet(frame: ByteArray, feet: Boolean): ByteArray? {
        if (parse(frame) == null) return null
        val old = frame[VALUE_AT + 5].toInt() and 0xFF
        return frame.copyOf().also { it[VALUE_AT + 5] = (if (feet) old or 1 else old and 1.inv()).toByte() }
    }

    val SOURCE_BYTES: Set<Int> = setOf(VALUE_AT)
    val APP_PORT_BYTES: Set<Int> = setOf(VALUE_AT + 3, VALUE_AT + 4)
    val FEET_BYTES: Set<Int> = setOf(VALUE_AT + 5)
}

/** Request 0x2E, units 3 to 5: one of the three user points, 15 value bytes. */
object TideUserPointCodec {
    const val FIRST_UNIT = 3
    const val POINTS = 3
    private const val VALUE_AT = 2
    const val VALUE_SIZE = 15

    /** [utcOffsetHours] is the point's offset from UTC in hours, [summerTime] bit 0 of the last byte. */
    data class Point(
        val hour: Int,
        val minute: Int,
        val latitude: Double,
        val longitude: Double,
        val utcOffsetHours: Float,
        val summerTime: Boolean,
    )

    fun unit(point: Int): Int = FIRST_UNIT + point - 1

    fun request(point: Int): ByteArray? = if (point in 1..POINTS) byteArrayOf(TideSettingCodec.CODE.toByte(), unit(point).toByte()) else null

    fun valid(p: Point): Boolean =
        p.hour in 0..23 && p.minute in 0..59 && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0 &&
            p.utcOffsetHours in -12f..14f

    /** The 15 value bytes of [p]; null when a field is out of range. */
    fun encode(p: Point): ByteArray? {
        if (!valid(p)) return null
        val out = ByteArray(VALUE_SIZE)
        out[0] = p.hour.toByte()
        out[1] = p.minute.toByte()
        putInt(out, 2, Math.round(p.latitude * 100).toInt())
        putInt(out, 6, Math.round(p.longitude * 100).toInt())
        putInt(out, 10, java.lang.Float.floatToRawIntBits(p.utcOffsetHours))
        out[14] = if (p.summerTime) 1 else 0
        return out
    }

    /** The value of a read frame (`2E unit` + value) for [point], or null when it is not that unit or too short. */
    fun parse(frame: ByteArray, point: Int): Point? {
        if (point !in 1..POINTS || frame.size < VALUE_AT + VALUE_SIZE) return null
        if (frame[0] != TideSettingCodec.CODE.toByte() || frame[1] != unit(point).toByte()) return null
        val v = frame.copyOfRange(VALUE_AT, VALUE_AT + VALUE_SIZE)
        val p = Point(
            v[0].toInt() and 0xFF, v[1].toInt() and 0xFF,
            getInt(v, 2) / 100.0, getInt(v, 6) / 100.0,
            java.lang.Float.intBitsToFloat(getInt(v, 10)), v[14].toInt() and 1 == 1,
        )
        return p.takeIf { valid(it) }
    }

    /** The frame with the 15 value bytes replaced; bytes beyond them stay. */
    fun withPoint(frame: ByteArray, point: Int, p: Point): ByteArray? {
        if (point !in 1..POINTS || frame.size < VALUE_AT + VALUE_SIZE) return null
        if (frame[0] != TideSettingCodec.CODE.toByte() || frame[1] != unit(point).toByte()) return null
        val value = encode(p) ?: return null
        return frame.copyOf().also { value.copyInto(it, VALUE_AT) }
    }

    val TOUCHED: Set<Int> = (VALUE_AT until VALUE_AT + VALUE_SIZE).toSet()

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        for (i in 0 until 4) b[at + i] = (v shr (8 * (3 - i))).toByte()
    }

    private fun getInt(b: ByteArray, at: Int): Int =
        (0 until 4).fold(0) { acc, i -> (acc shl 8) or (b[at + i].toInt() and 0xFF) }
}

/** The text fields of the own point form as a [TideUserPointCodec.Point]; null for anything that is not a plain, in range value. */
object TidePointText {
    fun parse(time: String, latitude: String, longitude: String, utcOffsetHours: String, summerTime: Boolean): TideUserPointCodec.Point? {
        val hm = time.trim().split(':')
        if (hm.size != 2) return null
        val hour = hm[0].trim().toIntOrNull() ?: return null
        val minute = hm[1].trim().toIntOrNull() ?: return null
        fun d(t: String) = t.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        val point = TideUserPointCodec.Point(
            hour, minute, d(latitude) ?: return null, d(longitude) ?: return null,
            (d(utcOffsetHours) ?: return null).toFloat(), summerTime,
        )
        return point.takeIf { TideUserPointCodec.valid(it) }
    }
}
