package org.avmedia.gshockapi.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class H5600WorkoutDecoderTest {
    @Test
    fun theSyntheticSummaryHasTheSizeOfTheNotes() {
        assertEquals(91, WorkoutTestData.runningSummary(withLoadFloat = false).size)
        assertEquals(95, WorkoutTestData.runningSummary().size)
    }

    @Test
    fun summarySizesPerSport() {
        val sizes = mapOf(1 to 91, 2 to 91, 4 to 70, 5 to 95, 6 to 95, 12 to 64, 8 to 66, 13 to 66, 11 to 42, 14 to 42, 9 to 119, 10 to 119)
        for ((sport, size) in sizes) assertEquals("sport $sport", size, WorkoutSummaryDecoder.sizeFor(sport))
        assertNull(WorkoutSummaryDecoder.sizeFor(99))
    }

    @Test
    fun headerDecodesTimesAndAddresses() {
        val h = WorkoutHeader.decode(WorkoutTestData.header(dataBytes = 178, lapBytes = 60, dataAddr = 8192, lapAddr = 12288))!!
        assertEquals(1, h.sport)
        assertEquals(4, h.formatVersion)
        assertTrue(h.hasGps)
        assertEquals(Instant.parse("2026-11-21T07:30:00Z").toEpochMilli(), h.startUtcMillis)
        assertEquals(Instant.parse("2026-11-21T08:01:05Z").toEpochMilli(), h.endUtcMillis)
        assertEquals(178L, h.dataBytes)
        assertEquals(8192L, h.dataAddress)
        assertEquals(12288L, h.lapAddress)
        assertEquals(0xFFFFFFFFL, h.minuteLapAddress)
        assertNull(WorkoutHeader.decode(ByteArray(100)))
    }

    @Test
    fun invalidBcdTimeIsNull() {
        val raw = WorkoutTestData.header()
        raw[35] = 0xFF.toByte()
        assertNull(WorkoutHeader.decode(raw)!!.startUtcMillis)
    }

    @Test
    fun runningSummaryValues() {
        val s = WorkoutSummaryDecoder.decode(WorkoutTestData.header(), 1, 4)!!
        assertEquals(31 * 60 + 5, s.totalSeconds)
        assertEquals(5.25, s.distance!!, 1e-6)
        assertEquals(5 * 60 + 54, s.avgPace)
        assertEquals(412L, s.kcal)
        assertEquals(listOf(60, 10, 30), s.fuel)
        assertEquals(88, s.cardioLoad)
        assertEquals(3, s.loadFeedback)
        assertEquals(34, s.runningIndex)
        assertEquals(150, s.hrAvg)
        assertEquals(178, s.hrMax)
        assertEquals(92, s.hrMin)
        assertEquals(120, s.altHigh)
        assertEquals(40L, s.ascent)
        assertEquals(170, s.cadenceAvg)
        assertEquals(4, s.lapCount)
        assertEquals(1990, s.profile["birthYear"])
        assertEquals(752, s.profile["weight"])
        assertEquals(88.5, s.cardioLoadFloat!!, 1e-6)
    }

    @Test
    fun oldFormatVersionHasNoFloatLoad() {
        val s = WorkoutSummaryDecoder.decode(WorkoutTestData.header(version = 3), 1, 3)!!
        assertNull(s.cardioLoadFloat)
        assertEquals(88, s.cardioLoad)
    }

    @Test
    fun noDataMarksBecomeNull() {
        val sum = WorkoutTestData.runningSummary()
        // distance float NaN, kcal all ones, average heart rate 0xFE, highest altitude 0x7FFF, pace not BCD
        for (i in 0 until 4) sum[1 + 3 + i] = 0xFF.toByte()
        for (i in 0 until 4) sum[14 + i] = 0xFF.toByte()
        sum[26 + 0] = 0xFE.toByte()
        sum[8] = 0xFF.toByte()
        val s = WorkoutSummaryDecoder.decode(WorkoutTestData.header(summary = sum), 1, 4)!!
        assertNull(s.distance)
        assertNull(s.kcal)
        assertNull(s.hrAvg)
        assertNull(s.avgPace)
        assertEquals(31 * 60 + 5, s.totalSeconds)
    }

    @Test
    fun swimSummary() {
        val b = Bytes().u8(0x89).hms(0, 20, 0).f(0.75f).ms(2, 10).ms(1, 50).ms(2, 40).u32(150).u16(12)
            .u8(30).u16(1990).u8(5).u8(14).u8(1).u16(180).u16(752).u8(0)
            .u8(2).u8(23).u8(0).u8(7).u8(0).u8(190).u8(52).u8(48)
            .u32(300).u32(25).u16(40).u16(35)
        for (i in 0 until 4) b.u32(100L * (i + 1)).hms(0, 5, 0).u32(30).u32(20).u16(40 + i)
        val sum = b.bytes()
        assertEquals(119, sum.size)
        val s = WorkoutSummaryDecoder.decode(WorkoutTestData.header(sport = 9, summary = sum), 9, 4)!!
        assertEquals(300L, s.swim["strokes"])
        assertEquals(35L, s.swim["swolfBest"])
        assertEquals(300L, s.swim["backMetres"])
        assertEquals(43L, s.swim["butterflySwolf"])
        assertNull(s.cardioLoadFloat)
    }

    @Test
    fun lapAndSampleRecordSizes() {
        val laps = mapOf(1 to 60, 4 to 29, 5 to 44, 12 to 25, 13 to 27, 14 to 11, 9 to 33, 10 to 24, 2 to 60, 6 to 44)
        for ((sport, size) in laps) assertEquals("lap $sport", size, WorkoutRecords.lapRecordSize(sport))
        assertNull(WorkoutRecords.lapRecordSize(8))
        assertNull(WorkoutRecords.lapRecordSize(11))
        val samples = mapOf(1 to 89, 4 to 50, 5 to 69, 12 to 46, 13 to 48, 14 to 22, 10 to 30)
        for ((sport, size) in samples) assertEquals("sample $sport", size, WorkoutRecords.sampleRecordSize(sport))
        assertNull(WorkoutRecords.sampleRecordSize(9))
        assertNull(WorkoutRecords.sampleRecordSize(8))
    }

    @Test
    fun runningLapsDecode() {
        val payload = WorkoutTestData.runningLap(0) + WorkoutTestData.runningLap(1)
        assertEquals(120, payload.size)
        val laps = WorkoutRecords.laps(1, payload)
        assertEquals(2, laps.size)
        assertEquals(5 * 60, laps[0].atSeconds)
        assertEquals(10 * 60, laps[1].atSeconds)
        assertEquals(300, laps[0].lapSeconds)
        assertFalse(laps[0].manual)
        assertEquals(1.0, laps[0].distance!!, 1e-6)
        assertEquals(300, laps[0].pace)
        assertEquals(150, laps[0].hrAvg)
        assertEquals(172, laps[0].cadence)
        assertEquals(2.0, laps[1].totalDistance!!, 1e-6)
        assertNull(laps[0].extras["asym"])
        assertEquals(0.0, laps[0].extras["contactL"]!!, 0.0)
    }

    @Test
    fun aCutRecordAtTheEndIsDropped() {
        val payload = WorkoutTestData.runningSample(0, 100) + WorkoutTestData.runningSample(1, 101)
        assertEquals(1, WorkoutRecords.samples(1, payload.copyOf(payload.size - 1)).size)
    }

    @Test
    fun runningSamplesDecode() {
        val payload = (0 until 3).fold(ByteArray(0)) { acc, i -> acc + WorkoutTestData.runningSample(i * 5, 120 + i) }
        val samples = WorkoutRecords.samples(1, payload)
        assertEquals(3, samples.size)
        assertEquals(5, samples[1].elapsedSeconds)
        assertEquals(121, samples[1].heartRate)
        assertEquals(25, samples[0].altitude)
        assertEquals(52.5, samples[0].latitude!!, 1e-9)
        assertEquals(4.9, samples[0].longitude!!, 1e-9)
        assertEquals(330, samples[0].pace)
        assertEquals(0.5, samples[0].extras["fuelC"]!!, 1e-6)
        assertEquals(1.0, samples[0].extras["formStatus"]!!, 0.0)
        assertFalse(samples[0].paused)
    }

    @Test
    fun blocksJoinByUsedBytesInChainOrder() {
        val a = WorkoutTestData.block(byteArrayOf(1, 2, 3), next = 8192)
        val b = WorkoutTestData.block(byteArrayOf(4, 5))
        assertEquals(8192L, WorkoutRecords.nextAddress(a))
        assertNull(WorkoutRecords.nextAddress(b))
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), WorkoutRecords.join(listOf(a, b)).toList())
    }

    @Test
    fun sessionAssemblesAndNotesTheUnconfirmedUnits() {
        val payload = (0 until 2).fold(ByteArray(0)) { acc, i -> acc + WorkoutTestData.runningSample(i, 100) }
        val laps = WorkoutTestData.runningLap(0)
        val h = WorkoutHeader.decode(WorkoutTestData.header(dataBytes = payload.size.toLong(), lapBytes = laps.size.toLong()))!!
        val s = WorkoutSessionBuilder.build(7, h, listOf(WorkoutTestData.block(payload)), listOf(WorkoutTestData.block(laps)))
        assertEquals(7, s.slot)
        assertEquals("Running", s.sportName)
        assertEquals(2, s.samples.size)
        assertEquals(1, s.laps.size)
        assertTrue(s.warnings.isEmpty())
        assertNotNull(s.unconfirmed["distance"])
        assertTrue(s.unconfirmed.values.all { "unconfirmed" in it })
    }

    @Test
    fun aTreadmillSportIsDecodedLikeRunningWithAWarning() {
        val h = WorkoutHeader.decode(WorkoutTestData.header(sport = 2))!!
        val s = WorkoutSessionBuilder.build(0, h, emptyList(), emptyList())
        assertTrue(s.warnings.any { "unconfirmed" in it })
        assertEquals("Treadmill running", s.sportName)
    }
}
