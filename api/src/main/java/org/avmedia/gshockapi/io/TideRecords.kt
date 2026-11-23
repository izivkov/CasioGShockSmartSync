package org.avmedia.gshockapi.io

import java.text.Normalizer

/**
 * The port record the watch is sent (bulk category 0x23), built from open constants. All numbers low byte
 * first. The 4 constituent form is the first test; the 60 form is built the same way and equally unproven on a watch.
 */
object TideRecords {
    const val FORM4_SIZE = 163
    const val FORM60_SIZE = 1009

    /** Port ids the watch has seen for each form; one slot only, so a fixed id per form. */
    const val FORM4_ID = 1
    const val FORM60_ID = 2405

    private const val NAME_AT = 1
    private const val NAME_BYTES = 18
    private const val NAME_USED = 17
    private const val LAT_AT = 19
    private const val LON_AT = 27
    private const val ZONE_AT = 35
    private const val SHIFT_AT = 36
    private const val RULE_AT = 37
    private const val NO_SUMMER_RULE_SHIFT = 4
    private const val TIME_BASE_GREENWICH = 7
    private const val MIN_60_CONSTANTS = 12
    private val MAIN = listOf("M2", "S2", "K1", "O1")
    private val CORE_FOR_60 = MAIN + listOf("N2", "K2", "P1", "Q1")

    fun z0Cm(s: TideStation): Double = s.z0Cm ?: MAIN.sumOf { s.constants[it]?.amplitudeCm ?: 0.0 }

    /** True when [z0Cm] is only the sum of the main amplitudes. */
    fun z0Approximate(s: TideStation): Boolean = s.z0Cm == null

    /** Graph pattern as the watch reads it: the number and the letter (A = 1) as digits, `12E` = 125. */
    fun graphPattern(s: TideStation): Int {
        val range = MAIN.sumOf { s.constants[it]?.amplitudeCm ?: 0.0 }
        val number = Math.round(13.4 - range / 52).toInt().coerceIn(2, 14)
        val letter = Math.round(5 - (z0Cm(s) - range) / 50).toInt().coerceIn(1, 8)
        return number * 10 + letter
    }

    fun can4(s: TideStation): Boolean = MAIN.all { (s.constants[it]?.amplitudeCm ?: 0.0) > 0.0 } && common(s)

    /** The 60 form needs the main eight and at least [MIN_60_CONSTANTS] constants in all. */
    fun can60(s: TideStation): Boolean =
        CORE_FOR_60.all { (s.constants[it]?.amplitudeCm ?: 0.0) > 0.0 } && s.constants.count { it.value.amplitudeCm > 0.0 } >= MIN_60_CONSTANTS && common(s)

    private fun common(s: TideStation): Boolean =
        s.latitude in -90.0..90.0 && s.longitude in -180.0..180.0 && s.zoneQuarterHours in -48..56 && s.constants.values.all { it.amplitudeCm in 0.0..2000.0 }

    fun build4(s: TideStation): ByteArray? {
        if (!can4(s)) return null
        val z0 = Math.round(z0Cm(s)).toInt()
        if (z0 !in -32768..32767) return null
        val out = head(s, 1, s.longitude)
        putU16(out, 38, z0)
        putU16(out, 40, graphPattern(s))
        MAIN.forEachIndexed { i, name ->
            val c = s.constants.getValue(name)
            putU16(out, 42 + 4 * i, wholeDegrees(c.phaseDeg))
            putU16(out, 44 + 4 * i, Math.round(c.amplitudeCm).toInt())
        }
        // f4, F4, f6, F6: shallow water terms from M4 and M6; 0 when missing or not representable.
        val m2 = s.constants.getValue("M2")
        val (f4, big4) = shallow("M4", 2, s, m2)
        val (f6, big6) = shallow("M6", 3, s, m2)
        putU16(out, 58, f4)
        putU16(out, 60, big4)
        putU16(out, 62, f6)
        putU16(out, 64, big6)
        // 66 to 161: both month sets stay 0; byte 162 = 0: the phases refer to Greenwich.
        return out
    }

    fun build60(s: TideStation): ByteArray? {
        if (!can60(s)) return null
        val out = head(s, 2, -s.longitude)
        putDouble(out, 38, z0Cm(s))
        putU16(out, 46, graphPattern(s))
        TideConstituents.ORDER.forEachIndexed { i, name ->
            val c = s.constants[name]
            putDouble(out, 48 + 8 * i, c?.amplitudeCm ?: 0.0)
            putDouble(out, 528 + 8 * i, if (c == null || c.amplitudeCm == 0.0) 0.0 else wholeDegrees(c.phaseDeg).toDouble())
        }
        out[1008] = TIME_BASE_GREENWICH.toByte()
        return out
    }

    private fun wholeDegrees(d: Double): Int = Math.round(((d % 360) + 360) % 360).toInt() % 360

    /** (f in degrees, F x 1000) for the shallow constituent [name] of order [order]; both 0 when it is missing or does not fit 16 bits. */
    private fun shallow(name: String, order: Int, s: TideStation, m2: TideConstant): Pair<Int, Int> {
        val c = s.constants[name] ?: return 0 to 0
        if (c.amplitudeCm <= 0.0 || m2.amplitudeCm <= 0.0) return 0 to 0
        val big = Math.round(1000 * (c.amplitudeCm / 100) / Math.pow(m2.amplitudeCm / 100, order.toDouble())).toInt()
        return if (big in 0..32767) wholeDegrees(c.phaseDeg - order * m2.phaseDeg) to big else 0 to 0
    }

    private fun head(s: TideStation, list: Int, longitudeOnWire: Double): ByteArray {
        val out = ByteArray(if (list == 1) FORM4_SIZE else FORM60_SIZE)
        out[0] = list.toByte()
        asciiName(s.name).copyInto(out, NAME_AT)
        putDouble(out, LAT_AT, s.latitude)
        putDouble(out, LON_AT, longitudeOnWire)
        out[ZONE_AT] = s.zoneQuarterHours.toByte()
        out[SHIFT_AT] = NO_SUMMER_RULE_SHIFT.toByte()
        out[RULE_AT] = 0
        return out
    }

    /** ASCII, at most [NAME_USED] bytes, zero filled to [NAME_BYTES]. */
    fun asciiName(name: String): ByteArray {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).filter { it.code in 32..126 }.trim().take(NAME_USED)
        return ByteArray(NAME_BYTES).also { plain.toByteArray(Charsets.US_ASCII).copyInto(it) }
    }

    private fun putU16(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte()
        b[at + 1] = (v shr 8).toByte()
    }

    private fun putDouble(b: ByteArray, at: Int, v: Double) {
        val bits = java.lang.Double.doubleToRawLongBits(v)
        for (i in 0 until 8) b[at + i] = (bits shr (8 * i)).toByte()
    }
}
