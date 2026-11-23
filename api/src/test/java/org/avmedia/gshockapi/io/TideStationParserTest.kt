package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "custom constants" text import. */
class TideStationParserTest {
    private val good = """
        # my port
        name Oostende
        lat 51.234
        lon 2.927
        utc 1
        z0 250
        M2 182.0 4.25
        s2, 53.2, 57.9
        K1 5.6 -12.2
        O1 9.4 167.3
        M8 1 2
    """.trimIndent()

    private fun ok(text: String) = TideStationParser.parse(text) as TideStationParser.Result.Ok

    private fun error(text: String) = (TideStationParser.parse(text) as TideStationParser.Result.Error).message

    @Test
    fun aGoodStationIsReadAndAnUnknownConstituentIsReported() {
        val r = ok(good)
        assertEquals("Oostende", r.station.name)
        assertEquals(4, r.station.zoneQuarterHours)
        assertEquals(250.0, r.station.z0Cm!!, 0.0)
        assertEquals(53.2, r.station.constants.getValue("S2").amplitudeCm, 0.0)
        assertEquals(-12.2, r.station.constants.getValue("K1").phaseDeg, 0.0)
        assertEquals(listOf("M8"), r.ignored)
        assertTrue(TideRecords.can4(r.station))
    }

    @Test
    fun missingPartsAreNamed() {
        assertTrue(error(good.replace("name Oostende", "")).contains("name"))
        assertTrue(error(good.replace("lat 51.234", "")).contains("lat"))
        assertTrue(error(good.replace("utc 1", "")).contains("utc"))
        assertTrue(error(good.replace("O1 9.4 167.3", "")).contains("O1"))
    }

    @Test
    fun badNumbersAndDuplicatesAreRefusedWithTheLine() {
        assertTrue(error(good.replace("lat 51.234", "lat north")).contains("Line"))
        assertTrue(error(good.replace("M2 182.0 4.25", "M2 182.0")).contains("Line"))
        assertTrue(error(good + "\nM2 1 1").contains("twice"))
        assertTrue(error(good.replace("M2 182.0 4.25", "M2 99999 4.25")).contains("amplitude"))
        assertTrue(error(good.replace("lat 51.234", "lat 95")).contains("range"))
        assertTrue(error(good.replace("utc 1", "utc 30")).contains("range"))
    }
}
