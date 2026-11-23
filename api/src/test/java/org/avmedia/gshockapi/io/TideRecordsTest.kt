package org.avmedia.gshockapi.io

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The port record builders (notes I3, I4, I6) on a fixture station, and the bundled stations. */
class TideRecordsTest {
    private val fixture = TideStation(
        name = "Oostende", latitude = 51.234, longitude = 2.927, zoneQuarterHours = 4,
        constants = mapOf(
            "M2" to TideConstant(200.0, 10.0), "S2" to TideConstant(60.0, 50.0), "K1" to TideConstant(6.0, 20.0),
            "O1" to TideConstant(9.0, 170.0), "M4" to TideConstant(12.0, 30.0),
        ),
        z0Cm = 260.0,
    )

    private val rich = fixture.copy(
        constants = fixture.constants + mapOf(
            "N2" to TideConstant(40.0, 5.5), "K2" to TideConstant(16.0, 51.0), "P1" to TideConstant(2.0, 10.0), "Q1" to TideConstant(3.0, 150.0),
            "M6" to TideConstant(8.0, -62.0), "2N2" to TideConstant(5.0, 1.0), "L2" to TideConstant(7.0, 20.0),
        ),
    )

    private fun le(b: ByteArray) = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)

    private fun s16(b: ByteArray, at: Int) = le(b).getShort(at).toInt()

    @Test
    fun theConstituentOrderIsTheSixtyOfTheRecord() {
        assertEquals(60, TideConstituents.ORDER.size)
        assertEquals(30, TideConstituents.ORDER.indexOf("M2"))
        assertEquals(35, TideConstituents.ORDER.indexOf("S2"))
        assertEquals(TideConstituents.ORDER.size, TideConstituents.ORDER.toSet().size)
        assertEquals("sigma1", TideConstituents.canonical("SGM"))
        assertEquals("MSf", TideConstituents.canonical("msf"))
        assertNull(TideConstituents.canonical("M8"))
    }

    @Test
    fun theGraphPatternFollowsTheRuleOfTheNotes() {
        // range 275 cm: 13.4 - 275/52 = 8.1 -> 8; letter 5 - (260 - 275)/50 = 5.3 -> 5 (E): 85.
        assertEquals(85, TideRecords.graphPattern(fixture))
    }

    @Test
    fun theFourConstituentRecordHasTheLayoutOfTheNotes() {
        val r = TideRecords.build4(fixture)!!
        assertEquals(163, r.size)
        assertEquals(1, r[0].toInt())
        assertEquals("Oostende", String(r, 1, 18, Charsets.US_ASCII).trimEnd('\u0000'))
        assertEquals(51.234, le(r).getDouble(19), 0.0)
        assertEquals(2.927, le(r).getDouble(27), 0.0)
        assertEquals(4, r[35].toInt())
        assertEquals(4, r[36].toInt())
        assertEquals(0, r[37].toInt())
        assertEquals(260, s16(r, 38))
        assertEquals(85, s16(r, 40))
        assertEquals(listOf(10, 200, 50, 60, 20, 6, 170, 9), (0 until 8).map { s16(r, 42 + 2 * it) })
        // f4 = 30 - 2 x 10 = 10 degrees, F4 = 0.12 / 2.0^2 = 0.03 = 30 per mille; no M6: f6 = F6 = 0.
        assertEquals(listOf(10, 30, 0, 0), (0 until 4).map { s16(r, 58 + 2 * it) })
        assertTrue((66 until 163).all { r[it].toInt() == 0 })
    }

    @Test
    fun theShallowTermsOfM6AreFilledWhenThereIsAnM6() {
        val r = TideRecords.build4(rich)!!
        // f6 = -62 - 3 x 10 = -92 = 268 degrees, F6 = 0.08 / 2.0^3 = 0.01 = 10 per mille.
        assertEquals(268, s16(r, 62))
        assertEquals(10, s16(r, 64))
    }

    @Test
    fun aMissingMainConstituentOrAnOutOfRangeValueMeansNoRecord() {
        assertNull(TideRecords.build4(fixture.copy(constants = fixture.constants - "O1")))
        assertNull(TideRecords.build4(fixture.copy(latitude = 91.0)))
        assertNull(TideRecords.build4(fixture.copy(zoneQuarterHours = 80)))
        assertNull(TideRecords.build4(fixture.copy(z0Cm = 40000.0)))
        assertNull(TideRecords.build4(fixture.copy(constants = fixture.constants + ("S2" to TideConstant(-1.0, 0.0)))))
        assertNull(TideRecords.build4(fixture.copy(constants = fixture.constants + ("S2" to TideConstant(5000.0, 0.0)))))
    }

    @Test
    fun withoutAKnownDatumZ0IsTheSumOfTheMainAmplitudesAndSaysSo() {
        val s = fixture.copy(z0Cm = null)
        assertEquals(275.0, TideRecords.z0Cm(s), 0.0)
        assertTrue(TideRecords.z0Approximate(s))
        assertFalse(TideRecords.z0Approximate(fixture))
        assertEquals(275, s16(TideRecords.build4(s)!!, 38))
    }

    @Test
    fun aPhaseIsWrappedIntoZeroTo359Degrees() {
        val s = fixture.copy(constants = fixture.constants + ("M2" to TideConstant(200.0, -12.4)))
        assertEquals(348, s16(TideRecords.build4(s)!!, 42))
        val t = fixture.copy(constants = fixture.constants + ("M2" to TideConstant(200.0, 359.6)))
        assertEquals(0, s16(TideRecords.build4(t)!!, 42))
    }

    @Test
    fun theNameIsAsciiAndAtMostSeventeenBytes() {
        val name = TideRecords.asciiName("Saint-Gilles-Croix-de-Vie, Cote atlantique")
        assertEquals(18, name.size)
        assertEquals(0, name[17].toInt())
        assertEquals("Saint-Gilles-Croi", String(name, 0, 17, Charsets.US_ASCII))
        assertEquals("Cadiz", String(TideRecords.asciiName("Cádiz"), 0, 5, Charsets.US_ASCII))
    }

    @Test
    fun theSixtyFormNeedsEnoughConstituents() {
        assertFalse(TideRecords.can60(fixture))
        assertNull(TideRecords.build60(fixture))
        assertTrue(TideRecords.can60(rich))
    }

    @Test
    fun theSixtyConstituentRecordHasTheLayoutOfTheNotes() {
        val r = TideRecords.build60(rich)!!
        assertEquals(1009, r.size)
        assertEquals(2, r[0].toInt())
        assertEquals(51.234, le(r).getDouble(19), 0.0)
        assertEquals(-2.927, le(r).getDouble(27), 0.0) // west positive in this form
        assertEquals(260.0, le(r).getDouble(38), 0.0)
        assertEquals(85, s16(r, 46))
        val m2 = TideConstituents.ORDER.indexOf("M2")
        assertEquals(200.0, le(r).getDouble(48 + 8 * m2), 0.0)
        assertEquals(10.0, le(r).getDouble(528 + 8 * m2), 0.0)
        val sa = TideConstituents.ORDER.indexOf("Sa")
        assertEquals(0.0, le(r).getDouble(48 + 8 * sa), 0.0)
        assertEquals(0.0, le(r).getDouble(528 + 8 * sa), 0.0)
        assertEquals(7, r[1008].toInt())
        assertEquals(60, (0 until 60).count { le(r).getDouble(48 + 8 * it) >= 0.0 })
        assertEquals(12, (0 until 60).count { le(r).getDouble(48 + 8 * it) > 0.0 })
    }

    @Test
    fun everyBundledStationBuildsTheFourFormAndHasAttribution() {
        assertEquals(
            listOf("Nieuwpoort", "Oostende", "Zeebrugge", "Vlissingen", "Hoek van Holland", "Dunkerque", "Calais", "Dover"),
            TideStations.ALL.map { it.name },
        )
        TideStations.ALL.forEach { s ->
            val r = TideRecords.build4(s)
            assertNotNull(s.name, r)
            assertEquals(163, r!!.size)
            assertNotNull(s.name, TideRecords.build60(s))
        }
        assertTrue(TideStations.ATTRIBUTION.contains("CC BY 4.0"))
        assertTrue(TideStations.ATTRIBUTION.contains("10.17882/109129"))
        val oostende = TideStations.ALL.first { it.name == "Oostende" }
        assertEquals(182.0, oostende.constants.getValue("M2").amplitudeCm, 0.1)
    }

    @Test
    fun theRecordBytesDoNotDependOnTheStationObjectIdentity() {
        assertArrayEquals(TideRecords.build4(fixture)!!, TideRecords.build4(fixture.copy())!!)
    }
}
