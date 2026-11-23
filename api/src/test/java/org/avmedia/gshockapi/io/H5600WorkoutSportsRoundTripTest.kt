package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One workout per sport of notes H3, built from the notes' tables H4 and H5 (token lists written out here by hand, not
 * taken from the decoder), sent through header, block chains and [WorkoutSessionBuilder]: summary, laps and samples.
 */
class H5600WorkoutSportsRoundTripTest {
    private val summaries = mapOf(
        "running" to "T time dist pace3 kcal fuel load rix hr3 alt2 climb cad2 stride vert laps CAL PROF TAIL loadf",
        "trail" to "T time dist pace3 kcal fuel load hr3 alt2 climb cad2 stride laps PROF TAIL loadf",
        "walking" to "T time dist pace3 speed2 kcal fuel load hr3 alt2 climb cad2 stride laps CAL PROF TAIL loadf",
        "trekking" to "T time dist pace3 kcal fuel load hr3 alt2 climb laps PROF TAIL loadf",
        "biking" to "T time dist speed2 kcal fuel load hr3 alt2 climb laps PROF TAIL loadf",
        "gym" to "T time kcal fuel load hr3 laps PROF TAIL loadf",
        "swim" to "T time dist pace3 kcal laps PROF TAIL SWIM",
    )

    /** Lap record after the 8-byte head (H5); null = the app reads none. */
    private val laps = mapOf(
        "running" to "f ms hr3 u16 u16 u16 u16 f u8 u16 u16 u16 u16 u16 u16 u16 u16 u16 u16 i16 u16 u16 u16 u16",
        "trail" to "f ms hr3 u16 u16 u16 u16 f",
        "walking" to "f ms f hr3 u16 u16 u16 u16 f u8 i16 u16 u16 u16 i16",
        "trekking" to "f ms hr3 u16 u16 f",
        "other" to "f f hr3 u16 u16 f",
        "interval" to "hr3",
        "pool" to "f ms f f u8 u16 u16 u16 u16 u16",
        "open" to "f ms f f u16",
    )

    /** Sample after the 8-byte head (H5); the first u8 is the heart rate. */
    private val samples = mapOf(
        "running" to "ms u8 u8 i16 u16 d d f u16 f f f u32 " + List(17) { "u16" }.joinToString(" ") + " u8",
        "trail" to "ms u8 u8 i16 u16 d d f u16 f f f",
        "walking" to "ms u8 u8 i16 u16 d d f f u16 f f f u32 u16 u16 i16 i16 u16 u8",
        "trekking" to "ms u8 u8 i16 d d f f f f",
        "other" to "u8 u8 i16 d d f f f f f",
        "interval" to "u8 u8 f f f",
        "open" to "ms d d f",
    )

    private class Kind(val summary: String, val lap: String?, val sample: String?)

    private val sports: Map<Int, Kind> = mapOf(
        1 to Kind("running", "running", "running"), 2 to Kind("running", "running", "running"), 3 to Kind("running", "running", "running"),
        4 to Kind("trail", "trail", "trail"), 5 to Kind("walking", "walking", "walking"), 6 to Kind("walking", "walking", "walking"),
        7 to Kind("walking", "walking", "walking"), 8 to Kind("biking", null, null), 9 to Kind("swim", "pool", null),
        10 to Kind("swim", "open", "open"), 11 to Kind("gym", null, null), 12 to Kind("trekking", "trekking", "trekking"),
        13 to Kind("biking", "other", "other"), 14 to Kind("gym", "interval", "interval"),
    )

    private fun summary(sport: Int, spec: String, lapCount: Int): ByteArray {
        val b = Bytes()
        for (t in spec.split(' ')) when (t) {
            "T" -> b.u8(sport + 0x80)
            "time" -> b.hms(0, 42, 10)
            "dist" -> b.f(6.5f)
            "pace3" -> b.ms(6, 0).ms(5, 30).ms(7, 0)
            "speed2" -> b.f(10f).f(14.5f)
            "kcal" -> b.u32(321)
            "fuel" -> b.u8(50).u8(30).u8(20)
            "load" -> b.u16(77).u8(2)
            "rix" -> b.u8(40).u8(3)
            "hr3" -> b.u8(140).u8(170).u8(90)
            "alt2" -> b.u16(120).u16(30)
            "climb" -> b.u32(55).u32(50)
            "cad2" -> b.u16(160).u16(175)
            "stride", "vert" -> b.u16(90)
            "laps" -> b.u16(lapCount)
            "CAL" -> b.pad(17)
            "PROF" -> b.u8(40).u16(1990).u8(1).u8(1).u8(1).u16(170).u16(700).u8(0)
            "TAIL" -> b.pad(8, 1)
            "loadf" -> b.f(77.5f)
            "SWIM" -> b.u32(900).u32(18).u16(40).u16(35).pad(68)
            else -> error(t)
        }
        return b.bytes()
    }

    /** Fields after a record head: the first f is the lap distance (1.0), hr3 is 150/160/140, the first u8 is [hr]. */
    private fun fields(spec: String, hr: Int): ByteArray {
        val b = Bytes()
        var firstF = true
        var firstU8 = true
        for (t in spec.split(' ')) when (t) {
            "f" -> { b.f(if (firstF) 1f else 0f); firstF = false }
            "d" -> b.d(0.0)
            "ms" -> b.ms(5, 0)
            "hr3" -> b.u8(150).u8(160).u8(140)
            "u8" -> { b.u8(if (firstU8) hr else 0); firstU8 = false }
            "u16", "i16" -> b.u16(0)
            "u32" -> b.u32(0)
            else -> error(t)
        }
        return b.bytes()
    }

    private fun lapRecord(sport: Int, spec: String, i: Int) =
        Bytes().u8(sport + 0x20).u8(0xFE).u8(0).bcd(5 * (i + 1)).bcd(0).hms(0, 5, 0).raw(fields(spec, 0)).bytes()

    private fun sampleRecord(sport: Int, spec: String, i: Int) =
        Bytes().u8(sport).u8(0).bcd(0).bcd(10 * i).u8(0).hms(0, 0, 0).raw(fields(spec, 120 + i)).bytes()

    @Test
    fun everySportOfTheNotesComesBackWithItsSummaryLapsAndSamples() {
        for ((sport, kind) in sports) {
            val lapBytes = kind.lap?.let { spec -> (0 until 2).fold(ByteArray(0)) { a, i -> a + lapRecord(sport, laps.getValue(spec), i) } } ?: ByteArray(0)
            val dataBytes = kind.sample?.let { spec -> (0 until 3).fold(ByteArray(0)) { a, i -> a + sampleRecord(sport, samples.getValue(spec), i) } } ?: ByteArray(0)
            val sum = summary(sport, summaries.getValue(kind.summary), if (kind.lap != null) 2 else 0)
            assertEquals("summary size, sport $sport", WorkoutSummaryDecoder.sizeFor(sport), sum.size)
            val header = WorkoutHeader.decode(
                WorkoutTestData.header(sport = sport, summary = sum, dataBytes = dataBytes.size.toLong(), lapBytes = lapBytes.size.toLong(), dataAddr = 8192, lapAddr = 12288),
            )!!
            val s = WorkoutSessionBuilder.build(sport, header, listOf(WorkoutTestData.block(dataBytes)), listOf(WorkoutTestData.block(lapBytes)))
            val m = "sport $sport"
            assertEquals(m, sport, s.sport)
            assertEquals(m, 42 * 60 + 10, s.summary.totalSeconds)
            assertEquals(m, 321L, s.summary.kcal)
            val spec = summaries.getValue(kind.summary)
            if ("hr3" in spec) assertEquals(m, listOf(140, 170, 90), listOf(s.summary.hrAvg, s.summary.hrMax, s.summary.hrMin)) else assertNull(m, s.summary.hrAvg)
            if ("dist" in spec) assertEquals(m, 6.5, s.summary.distance!!, 1e-6) else assertNull(m, s.summary.distance)
            if ("pace3" in spec) assertEquals(m, 360, s.summary.avgPace) else assertNull(m, s.summary.avgPace)
            if ("speed2" in spec) assertEquals(m, 14.5, s.summary.maxSpeed!!, 1e-6) else assertNull(m, s.summary.maxSpeed)
            if ("climb" in spec) assertEquals(m, 55L, s.summary.ascent) else assertNull(m, s.summary.ascent)
            if ("loadf" in spec) assertEquals(m, 77.5, s.summary.cardioLoadFloat!!, 1e-6) else assertNull(m, s.summary.cardioLoadFloat)
            if ("SWIM" in spec) assertEquals(m, 900L, s.summary.swim["strokes"])
            assertEquals(m, 170, s.summary.profile["height"])
            assertEquals(m, if (kind.lap != null) 2 else 0, s.summary.lapCount)
            if (kind.lap == null) {
                assertTrue(m, s.laps.isEmpty())
            } else {
                assertEquals(m, 2, s.laps.size)
                assertEquals(m, 600, s.laps[1].atSeconds)
                assertEquals(m, 300, s.laps[0].lapSeconds)
                if ("hr3" in laps.getValue(kind.lap)) assertEquals(m, listOf(150, 160), listOf(s.laps[0].hrAvg, s.laps[0].hrMax)) else assertNull(m, s.laps[0].hrAvg)
                if (kind.lap != "interval") assertEquals(m, 1.0, s.laps[0].distance!!, 1e-6)
            }
            if (kind.sample == null) {
                assertTrue(m, s.samples.isEmpty())
            } else {
                assertEquals(m, 3, s.samples.size)
                assertEquals(m, 20, s.samples[2].elapsedSeconds)
                if (kind.sample != "open") assertEquals(m, listOf(120, 121, 122), s.samples.map { it.heartRate })
                if (kind.sample != "other" && kind.sample != "interval") assertEquals(m, 300, s.samples[0].pace)
            }
            assertTrue("$m: ${s.warnings}", s.warnings.none { "cut" in it || "announces" in it || "not decoded" in it })
        }
    }
}
