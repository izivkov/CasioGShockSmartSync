package org.avmedia.gshockapi.io

/**
 * "Custom constants": a station typed or pasted as text. One item per line, `#` starts a comment:
 * ```
 * name Oostende
 * lat 51.234          (degrees, north positive)
 * lon 2.927           (degrees, east positive)
 * utc 1               (hours east of UTC, standard time; quarter hours allowed)
 * z0 250              (optional: mean level above chart datum, cm)
 * M2 182.0 4.25       (constituent, amplitude in cm, Greenwich phase lag in degrees; a comma may replace the space)
 * ```
 * Names are read as TICON or NOAA write them. A constituent the watch has no place for is reported, not skipped silently.
 */
object TideStationParser {
    sealed class Result {
        data class Ok(val station: TideStation, val ignored: List<String>) : Result()

        data class Error(val message: String) : Result()
    }

    fun parse(text: String): Result {
        var name: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var utc: Double? = null
        var z0: Double? = null
        val constants = LinkedHashMap<String, TideConstant>()
        val ignored = ArrayList<String>()
        text.lines().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val parts = line.split(Regex("[\\s,;=]+"))
            val key = parts[0].lowercase()
            fun num(i: Int): Double? = parts.getOrNull(i)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }
            when (key) {
                "name" -> name = line.substring(parts[0].length).trim(' ', '=', ':').ifEmpty { null }
                "lat" -> lat = num(1) ?: return Result.Error("Line ${index + 1}: latitude is not a number.")
                "lon" -> lon = num(1) ?: return Result.Error("Line ${index + 1}: longitude is not a number.")
                "utc" -> utc = num(1) ?: return Result.Error("Line ${index + 1}: the UTC offset is not a number.")
                "z0" -> z0 = num(1) ?: return Result.Error("Line ${index + 1}: z0 is not a number.")
                else -> {
                    val amp = num(1)
                    val pha = num(2)
                    if (amp == null || pha == null) return Result.Error("Line ${index + 1}: expected a constituent, its amplitude (cm) and its phase lag (degrees).")
                    if (amp < 0 || amp > 2000) return Result.Error("Line ${index + 1}: the amplitude of ${parts[0]} is outside 0 to 2000 cm.")
                    val canonical = TideConstituents.canonical(parts[0])
                    if (canonical == null) ignored += parts[0] else if (canonical in constants) return Result.Error("Line ${index + 1}: ${parts[0]} twice.") else constants[canonical] = TideConstant(amp, pha)
                }
            }
        }
        val n = name ?: return Result.Error("The station has no name line.")
        val la = lat ?: return Result.Error("The station has no lat line.")
        val lo = lon ?: return Result.Error("The station has no lon line.")
        val offset = utc ?: return Result.Error("The station has no utc line (hours east of UTC).")
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return Result.Error("Latitude or longitude is out of range.")
        val quarters = Math.round(offset * 4).toInt()
        if (quarters !in -48..56) return Result.Error("The UTC offset is out of range.")
        val station = TideStation(n, la, lo, quarters, constants, z0)
        if (!TideRecords.can4(station)) return Result.Error("M2, S2, K1 and O1 are needed, each with an amplitude above 0.")
        return Result.Ok(station, ignored)
    }
}
