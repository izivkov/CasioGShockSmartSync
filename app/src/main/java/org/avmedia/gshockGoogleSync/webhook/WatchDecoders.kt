package org.avmedia.gshockGoogleSync.webhook

/** Decoders for raw watch answers ("0x28 06 1D ..."): webhook variables never carry raw hex. */
object WatchDecoders {
    private val hexByte = Regex("^[0-9A-Fa-f][0-9A-Fa-f]$")

    /** "0x1F 00 42 .." or "1F 00 42 .." to the byte values; null when any token is not a hex byte. */
    fun bytes(raw: String): List<Int>? {
        val tokens = raw.trim().split(' ', '\t').filter { it.isNotEmpty() }.toMutableList()
        if (tokens.isEmpty()) return null
        tokens[0] = tokens[0].removePrefix("0x").removePrefix("0X")
        if (!tokens.all { hexByte.matches(it) }) return null
        return tokens.map { it.toInt(16) }
    }

    /** True for text that is just a hex dump ("0x1D 00 01 ..."), which must never go out as a value. */
    fun looksLikeRawHex(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("0x", ignoreCase = true) && bytes(t) != null
    }

    // Watch condition (request 0x28) of the H5600 family: [1] battery step 0..6, [2] temperature.
    private val BATTERY_LABELS = listOf("CHG", "LOW", "1", "2", "3", "4", "HIGH")

    data class Condition(val batteryLevel: Int?, val temperature: Int?) {
        val batteryLabel: String? get() = batteryLevel?.let { BATTERY_LABELS[it] }

        /** 0..6 to 0..100 percent. */
        val batteryPercent: Int? get() = batteryLevel?.let { Math.round(it * 100f / 6) }
    }

    /** Level 0 CHG, 1 LOW, 2-5 = 1-4 bars, 6 HIGH. Anything else, and a short or wrong answer, gives null. */
    fun h5600Condition(answer: String?): Condition? {
        val b = answer?.let { bytes(it) } ?: return null
        if (b.size < 3 || b[0] != 0x28) return null
        val level = b[1].takeIf { it in 0..6 }
        val temp = b[2].let { if (it > 127) it - 256 else it }.takeIf { it in -40..80 }
        return Condition(level, temp)
    }

    /**
     * Version answer (request 0x20, "0x20 23 07 00 12 ..."): the bytes after the code, as hex pairs. Nothing
     * in the library or its Python twin decodes them, so their meaning is not guessed. Null for a wrong answer.
     */
    fun versionRaw(answer: String?): String? {
        val b = answer?.let { bytes(it) } ?: return null
        if (b.size < 2 || b[0] != 0x20) return null
        return b.drop(1).joinToString(" ") { "%02X".format(it) }
    }

    /** Answer to a raw setting request: all bytes as hex pairs. Not implemented yet. */
    fun rawAnswer(answer: String?, code: Int): String? {
        val b = answer?.let { bytes(it) } ?: return null
        if (b.size < 2 || b[0] != code) return null
        return b.joinToString(" ") { "%02X".format(it) }
    }

    /** "unknown" while the version bytes are read but not decodable; null when there was no answer. */
    fun firmwareState(answer: String?): String? = versionRaw(answer)?.let { "unknown" }

    /** Home-time daylight saving (request 1D 00: "1D 00 01 DST0 DST1 ..."): bit 0 = DST on. */
    fun dstHomeOn(raw: String?): Boolean? {
        val b = raw?.let { bytes(it) } ?: return null
        if (b.size < 5 || b[0] != 0x1D) return null
        return b[3] and 1 == 1
    }

    /**
     * World city name from a 0x1F answer ("1F <index> <ASCII name> 00 ..."). Slots without a city return
     * garbage; a name is accepted only when the index matches and every byte is a letter, digit, space or . - '.
     */
    fun worldCityName(raw: String?, index: Int): String? {
        val b = raw?.let { bytes(it) } ?: return null
        if (b.size < 3 || b[0] != 0x1F || b[1] != index) return null
        val name = StringBuilder()
        for (v in b.drop(2)) {
            if (v == 0) break
            val c = v.toChar()
            if (!(c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' || c == '.' || c == '-' || c == '\'')) return null
            name.append(c)
        }
        return name.toString().trim().takeIf { s -> s.any { it.isLetter() } }
    }
}
