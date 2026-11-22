package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.WorkoutSummary

/** Decodes the summary from workout header offset 69 (notes H4). */
object WorkoutSummaryDecoder {
    const val OFFSET = 69

    private const val T = "u8:type"
    private const val TIME = "hms:time"
    private const val DIST = "f:dist"
    private const val PACE3 = "ms:paceAvg ms:paceFast ms:paceSlow"
    private const val SPEED2 = "f:speedAvg f:speedMax"
    private const val KCAL = "u32:kcal"
    private const val FUEL = "u8:fuelC u8:fuelP u8:fuelF"
    private const val LOAD = "u16:load i8:loadFeedback"
    private const val RIX = "u8:rix u8:rixRating"
    private const val HR3 = "hr3"
    private const val ALT2 = "i16:altHigh i16:altLow"
    private const val CLIMB = "u32:ascent u32:descent"
    private const val CAD2 = "u16:cadAvg u16:cadMax"
    private const val STRIDE = "u16:stride"
    private const val VERT = "u16:vert"
    private const val LAPS = "u16:laps"
    private const val CAL = "skip:17"
    private const val PROF = "u8:age u16:birthYear u8:birthMonth u8:birthDay u8:male u16:height u16:weight u8:hand"
    private const val TAIL = "u8:background u8:bedH u8:bedM u8:wakeH u8:wakeM u8:hrHighest u8:hrRest u8:vo2max"
    private const val LOADF = "f:loadf"
    private const val STYLE = "u32:metres hms:time u32:strokes u32:energy u16:swolf"
    private const val SWIM = "u32:strokes u32:strokesPerLap u16:swolfAvg u16:swolfBest skip:68"

    private fun layout(vararg parts: String) = WorkoutLayout(parts.joinToString(" "))

    private val RUNNING = layout(T, TIME, DIST, PACE3, KCAL, FUEL, LOAD, RIX, HR3, ALT2, CLIMB, CAD2, STRIDE, VERT, LAPS, CAL, PROF, TAIL)
    private val TRAIL = layout(T, TIME, DIST, PACE3, KCAL, FUEL, LOAD, HR3, ALT2, CLIMB, CAD2, STRIDE, LAPS, PROF, TAIL)
    private val WALKING = layout(T, TIME, DIST, PACE3, SPEED2, KCAL, FUEL, LOAD, HR3, ALT2, CLIMB, CAD2, STRIDE, LAPS, CAL, PROF, TAIL)
    private val TREKKING = layout(T, TIME, DIST, PACE3, KCAL, FUEL, LOAD, HR3, ALT2, CLIMB, LAPS, PROF, TAIL)
    private val BIKING = layout(T, TIME, DIST, SPEED2, KCAL, FUEL, LOAD, HR3, ALT2, CLIMB, LAPS, PROF, TAIL)
    private val GYM = layout(T, TIME, KCAL, FUEL, LOAD, HR3, LAPS, PROF, TAIL)
    private val SWIMMING = layout(T, TIME, DIST, PACE3, KCAL, LAPS, PROF, TAIL, SWIM)
    private val LOAD_FLOAT = WorkoutLayout(LOADF)

    private fun layoutFor(family: Int): WorkoutLayout? = when (family) {
        WorkoutSportFamily.RUNNING -> RUNNING
        WorkoutSportFamily.TRAIL -> TRAIL
        WorkoutSportFamily.WALKING -> WALKING
        WorkoutSportFamily.TREKKING -> TREKKING
        WorkoutSportFamily.BIKING, WorkoutSportFamily.OTHER -> BIKING
        WorkoutSportFamily.GYM, WorkoutSportFamily.INTERVAL -> GYM
        WorkoutSportFamily.POOL, WorkoutSportFamily.OPEN_WATER -> SWIMMING
        else -> null
    }

    /** Summary bytes the notes list for the family (without the float load); null for an unknown sport. */
    fun sizeFor(sport: Int): Int? = WorkoutSportFamily.of(sport)?.let { layoutFor(it) }?.size

    /** Null when the sport is unknown or the header is too short for the summary. */
    fun decode(header: ByteArray, sport: Int, formatVersion: Int): WorkoutSummary? {
        val family = WorkoutSportFamily.of(sport) ?: return null
        val layout = layoutFor(family) ?: return null
        val swim = family == WorkoutSportFamily.POOL || family == WorkoutSportFamily.OPEN_WATER
        val withLoadF = !swim && formatVersion >= 4
        val need = layout.size + if (withLoadF) LOAD_FLOAT.size else 0
        if (header.size < OFFSET + need) return null
        val c = WorkoutCursor(header, OFFSET)
        val v = layout.read(c)
        val loadf = if (withLoadF) LOAD_FLOAT.read(c)["loadf"] else null
        fun i(k: String) = v[k]?.toInt()
        val swimMap: Map<String, Long> = if (swim) {
            val sc = WorkoutCursor(header, OFFSET + layout.size - 68)
            val styles = listOf("free", "breast", "back", "butterfly")
            buildMap<String, Long> {
                for (k in listOf("strokes", "strokesPerLap", "swolfAvg", "swolfBest")) v[k]?.let { put(k, it.toLong()) }
                for (s in styles) {
                    val st = WorkoutLayout(STYLE).read(sc)
                    for ((k, x) in st) x?.let { put(s + k.replaceFirstChar { ch -> ch.uppercase() }, it.toLong()) }
                }
            }
        } else {
            emptyMap<String, Long>()
        }
        val profile = buildMap<String, Int> {
            for (k in listOf("age", "birthYear", "birthMonth", "birthDay", "male", "height", "weight", "hand")) v[k]?.let { put(k, it.toInt()) }
        }
        val fuel = if (v.containsKey("fuelC")) listOf(i("fuelC"), i("fuelP"), i("fuelF")) else null
        return WorkoutSummary(
            totalSeconds = i("time"),
            distance = v["dist"],
            avgPace = i("paceAvg"),
            fastestPace = i("paceFast"),
            slowestPace = i("paceSlow"),
            avgSpeed = v["speedAvg"],
            maxSpeed = v["speedMax"],
            kcal = v["kcal"]?.toLong(),
            fuel = fuel,
            cardioLoad = i("load"),
            loadFeedback = i("loadFeedback"),
            runningIndex = i("rix"),
            runningRating = i("rixRating"),
            hrAvg = i("hrAvg"),
            hrMax = i("hrMax"),
            hrMin = i("hrMin"),
            altHigh = i("altHigh"),
            altLow = i("altLow"),
            ascent = v["ascent"]?.toLong(),
            descent = v["descent"]?.toLong(),
            cadenceAvg = i("cadAvg"),
            cadenceMax = i("cadMax"),
            strideAvg = i("stride"),
            verticalAvg = i("vert"),
            lapCount = i("laps"),
            cardioLoadFloat = loadf,
            swim = swimMap,
            profile = profile,
        )
    }
}
