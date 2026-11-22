package org.avmedia.gshockapi.io

/**
 * Field layouts of the workout records as short strings, for example `"f:dist ms:pace hr3 u16:asc skip:4"`.
 * Types: u8 u16 u32 i8 i16 f (float) d (double) ms (BCD minutes, seconds, read as seconds) hms (BCD, read as seconds),
 * hr3 (average, highest, lowest heart rate as hrAvg hrMax hrMin) and skip:N. "No data" values read as null.
 */
class WorkoutLayout(spec: String) {
    private class Tok(val type: String, val name: String)

    private val toks: List<Tok> = spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
        val parts = it.split(':')
        Tok(parts[0], parts.getOrElse(1) { "" })
    }

    /** Bytes one record of this layout takes. */
    val size: Int = toks.sumOf { sizeOf(it) }

    private fun sizeOf(t: Tok): Int = when (t.type) {
        "u8", "i8" -> 1
        "u16", "i16", "ms" -> 2
        "u32", "f" -> 4
        "d" -> 8
        "hms", "hr3" -> 3
        "skip" -> t.name.toInt()
        else -> error("unknown layout type ${t.type}")
    }

    /** Reads one record; the cursor must have [size] bytes left. Names map to null for "no data". */
    fun read(c: WorkoutCursor): Map<String, Double?> {
        val out = LinkedHashMap<String, Double?>()
        for (t in toks) {
            when (t.type) {
                "u8" -> out[t.name] = WorkoutCodec.unsigned(c.u8().toLong(), 8)?.toDouble()
                "u16" -> out[t.name] = WorkoutCodec.unsigned(c.u16().toLong(), 16)?.toDouble()
                "u32" -> out[t.name] = WorkoutCodec.unsigned(c.u32(), 32)?.toDouble()
                "i8" -> out[t.name] = WorkoutCodec.signed(c.i8(), 8)?.toDouble()
                "i16" -> out[t.name] = WorkoutCodec.signed(c.i16(), 16)?.toDouble()
                "f" -> out[t.name] = WorkoutCodec.finite(c.f32())
                "d" -> out[t.name] = WorkoutCodec.finite(c.f64())
                "ms" -> out[t.name] = WorkoutCodec.ms(c.u8(), c.u8())?.toDouble()
                "hms" -> out[t.name] = WorkoutCodec.hms(c.u8(), c.u8(), c.u8())?.toDouble()
                "hr3" -> {
                    out["hrAvg"] = WorkoutCodec.unsigned(c.u8().toLong(), 8)?.toDouble()
                    out["hrMax"] = WorkoutCodec.unsigned(c.u8().toLong(), 8)?.toDouble()
                    out["hrMin"] = WorkoutCodec.unsigned(c.u8().toLong(), 8)?.toDouble()
                }
                "skip" -> c.skip(t.name.toInt())
            }
        }
        return out
    }
}

/** Sport to the layout family it is decoded with. Treadmill and motion sensor kinds follow running and walking (likely). */
object WorkoutSportFamily {
    const val RUNNING = 1
    const val TRAIL = 4
    const val WALKING = 5
    const val BIKING = 8
    const val POOL = 9
    const val OPEN_WATER = 10
    const val GYM = 11
    const val TREKKING = 12
    const val OTHER = 13
    const val INTERVAL = 14

    /** The family code, or null for a sport code the notes do not list. */
    fun of(sport: Int): Int? = when (sport) {
        1, 2, 3 -> RUNNING
        4 -> TRAIL
        5, 6, 7 -> WALKING
        in 8..14 -> sport
        else -> null
    }

    /** Sports whose layout is a guess from a related one. */
    fun isInferred(sport: Int): Boolean = sport in setOf(2, 3, 6, 7)
}
