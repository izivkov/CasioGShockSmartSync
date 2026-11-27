package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.WorkoutLap
import org.avmedia.gshockapi.model.WorkoutSample

/** Block chains and the lap and sample records cut from their payload. */
object WorkoutRecords {
    const val BLOCK_SIZE = 4096
    private const val PAYLOAD_FROM = 16
    private const val LAST = 0xFFFFFFFFL
    private const val HEAD = 8
    private const val RUNNING_FORM = "cadL cadR strideL strideR contactL contactR flightL flightR shareL shareR vertL vertR dropL dropR stiffL stiffR asym"

    /** Used payload bytes of one block (offset 2, clamped to the block). */
    fun blockPayload(block: ByteArray): ByteArray {
        if (block.size < PAYLOAD_FROM) return ByteArray(0)
        val used = WorkoutCursor(block, 2).u16().coerceIn(0, minOf(block.size, BLOCK_SIZE) - PAYLOAD_FROM)
        return block.copyOfRange(PAYLOAD_FROM, PAYLOAD_FROM + used)
    }

    /** Address of the next block, or null for the last one (all ones) or a short block. */
    fun nextAddress(block: ByteArray): Long? {
        if (block.size < 11) return null
        val a = WorkoutCursor(block, 7).u32()
        return if (a == LAST) null else a
    }

    /** Joins the payloads of the blocks of one chain in order. */
    fun join(blocks: List<ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (b in blocks) out.write(blockPayload(b))
        return out.toByteArray()
    }

    private fun lapLayout(family: Int): WorkoutLayout? =
        when (family) {
            WorkoutSportFamily.RUNNING -> LAP_RUNNING
            WorkoutSportFamily.TRAIL -> LAP_TRAIL
            WorkoutSportFamily.WALKING -> LAP_WALKING
            WorkoutSportFamily.TREKKING -> LAP_TREKKING
            WorkoutSportFamily.OTHER -> LAP_OTHER
            WorkoutSportFamily.INTERVAL -> LAP_INTERVAL
            WorkoutSportFamily.POOL -> LAP_POOL
            WorkoutSportFamily.OPEN_WATER -> LAP_OPEN
            else -> null
        }

    private fun sampleLayout(family: Int): WorkoutLayout? =
        when (family) {
            WorkoutSportFamily.RUNNING -> SAMPLE_RUNNING
            WorkoutSportFamily.TRAIL -> SAMPLE_TRAIL
            WorkoutSportFamily.WALKING -> SAMPLE_WALKING
            WorkoutSportFamily.TREKKING -> SAMPLE_TREKKING
            WorkoutSportFamily.OTHER -> SAMPLE_OTHER
            WorkoutSportFamily.INTERVAL -> SAMPLE_INTERVAL
            WorkoutSportFamily.OPEN_WATER -> SAMPLE_OPEN
            else -> null
        }

    /** Record size in bytes including the 8 byte head; null when the sport has no records. */
    fun lapRecordSize(sport: Int): Int? = WorkoutSportFamily.of(sport)?.let { lapLayout(it) }?.let { it.size + HEAD }

    fun sampleRecordSize(sport: Int): Int? = WorkoutSportFamily.of(sport)?.let { sampleLayout(it) }?.let { it.size + HEAD }

    /** Cuts [payload] into lap records. A cut record at the end is dropped. */
    fun laps(
        sport: Int,
        payload: ByteArray,
    ): List<WorkoutLap> {
        val layout = WorkoutSportFamily.of(sport)?.let { lapLayout(it) } ?: return emptyList()
        val size = layout.size + HEAD
        val out = ArrayList<WorkoutLap>()
        var at = 0
        while (at + size <= payload.size) {
            val c = WorkoutCursor(payload, at)
            c.u8()
            val manualFlag = c.u8()
            val atSec = hour(c)
            val lapSec = WorkoutCodec.hms(c.u8(), c.u8(), c.u8())
            val v = layout.read(c)
            out +=
                WorkoutLap(
                    index = out.size,
                    atSeconds = atSec,
                    lapSeconds = lapSec,
                    manual = manualFlag != 0xFE,
                    distance = v["dist"], pace = v["pace"]?.toInt(), speed = v["speed"],
                    hrAvg = v["hrAvg"]?.toInt(), hrMax = v["hrMax"]?.toInt(), hrMin = v["hrMin"]?.toInt(),
                    ascent = v["asc"]?.toInt(), descent = v["desc"]?.toInt(), cadence = v["cad"]?.toInt(),
                    stride = v["stride"]?.toInt(), totalDistance = v["total"],
                    extras = extras(v, LAP_KNOWN),
                )
            at += size
        }
        return out
    }

    /** Cuts [payload] into samples. A cut record at the end is dropped. */
    fun samples(
        sport: Int,
        payload: ByteArray,
    ): List<WorkoutSample> {
        val layout = WorkoutSportFamily.of(sport)?.let { sampleLayout(it) } ?: return emptyList()
        val size = layout.size + HEAD
        val out = ArrayList<WorkoutSample>()
        var at = 0
        while (at + size <= payload.size) {
            val c = WorkoutCursor(payload, at)
            c.u8()
            val elapsed = hour(c)
            val pause = c.u8()
            c.skip(3)
            val v = layout.read(c)
            out +=
                WorkoutSample(
                    elapsedSeconds = elapsed,
                    paused = pause == 1,
                    pace = v["pace"]?.toInt(), heartRate = v["hr"]?.toInt(), hrReliability = v["hrRel"]?.toInt(),
                    altitude = v["alt"]?.toInt(), cadence = v["cad"]?.toInt(),
                    latitude = v["lat"], longitude = v["lon"], speed = v["speed"], totalDistance = v["total"],
                    stride = v["stride"]?.toInt(),
                    extras = extras(v, SAMPLE_KNOWN),
                )
            at += size
        }
        return out
    }

    /** u8 hours, BCD minutes, BCD seconds to seconds. */
    private fun hour(c: WorkoutCursor): Int? {
        val h = c.u8()
        val m = WorkoutCodec.bcd(c.u8())
        val s = WorkoutCodec.bcd(c.u8())
        return if (m == null || s == null || h == 0xFF) null else h * 3600 + m * 60 + s
    }

    private fun extras(
        v: Map<String, Double?>,
        known: Set<String>,
    ): Map<String, Double> = v.filterKeys { it !in known }.mapNotNull { (k, x) -> x?.let { k to it } }.toMap()

    private val LAP_KNOWN = setOf("dist", "pace", "speed", "hrAvg", "hrMax", "hrMin", "asc", "desc", "cad", "stride", "total")
    private val SAMPLE_KNOWN = setOf("pace", "hr", "hrRel", "alt", "cad", "lat", "lon", "speed", "total", "stride")

    private val LAP_RUNNING =
        WorkoutLayout(
            "f:dist ms:pace hr3 u16:asc u16:desc u16:cad u16:stride f:total u8:pause u16:contactL u16:contactR u16:shareL u16:shareR " +
                "u16:vertL u16:vertR u16:stiffL u16:stiffR u16:dropL u16:dropR i16:asym skip:4 u16:strideL u16:strideR",
        )
    private val LAP_TRAIL = WorkoutLayout("f:dist ms:pace hr3 u16:asc u16:desc u16:cad u16:stride f:total")
    private val LAP_WALKING =
        WorkoutLayout("f:dist ms:pace f:speed hr3 u16:asc u16:desc u16:cad u16:stride f:total u8:pause i16:cadDiff skip:6 i16:cadRatio")
    private val LAP_TREKKING = WorkoutLayout("f:dist ms:pace hr3 u16:asc u16:desc f:total")
    private val LAP_OTHER = WorkoutLayout("f:dist f:speed hr3 u16:asc u16:desc f:total")
    private val LAP_INTERVAL = WorkoutLayout("hr3")
    private val LAP_POOL =
        WorkoutLayout("f:dist ms:pace f:speed f:total u8:strokeKind u16:strokes u16:swolfFree u16:swolfBreast u16:swolfBack u16:swolfFly")
    private val LAP_OPEN = WorkoutLayout("f:dist ms:pace f:speed f:total u16:strokes")

    private const val FUEL = "f:fuelC f:fuelP f:fuelF"
    private const val FIX = "u8:hr u8:hrRel i16:alt"
    private val SAMPLE_RUNNING =
        WorkoutLayout(
            "ms:pace $FIX u16:cad d:lat d:lon f:total u16:stride $FUEL u32:analysisMs " +
                RUNNING_FORM.split(' ').joinToString(" ") { "u16:$it" } + " u8:formStatus",
        )
    private val SAMPLE_TRAIL = WorkoutLayout("ms:pace $FIX u16:cad d:lat d:lon f:total u16:stride $FUEL")
    private val SAMPLE_WALKING =
        WorkoutLayout(
            "ms:pace $FIX u16:cad d:lat d:lon f:speed f:total u16:stride $FUEL u32:analysisMs u16:walkA u16:walkB i16:walkC i16:walkD u16:walkForm u8:formStatus",
        )
    private val SAMPLE_TREKKING = WorkoutLayout("ms:pace $FIX d:lat d:lon f:total $FUEL")
    private val SAMPLE_OTHER = WorkoutLayout("$FIX d:lat d:lon f:speed f:total $FUEL")
    private val SAMPLE_INTERVAL = WorkoutLayout("u8:hr u8:hrRel $FUEL")
    private val SAMPLE_OPEN = WorkoutLayout("ms:pace d:lat d:lon f:total")
}
