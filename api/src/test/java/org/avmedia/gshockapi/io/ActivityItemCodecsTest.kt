package org.avmedia.gshockapi.io

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 0x54 wrapper and the per-activity codecs, on frames built from the documented layouts. */
class ActivityItemCodecsTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun changed(a: ByteArray, b: ByteArray) = a.indices.filter { a[it] != b[it] }.toSet()

    @Test
    fun theReadRequestIs54ZeroActivityClassPart() {
        assertArrayEquals(bytes(0x54, 0, 1, 0x40, 0), ActivityItemCodec.request(1, 0x40))
        assertArrayEquals(bytes(0x54, 0, 9, 0x46, 1), ActivityItemCodec.request(9, 0x46, 1))
    }

    // Run measurement of running: flags 0b1010 (auto pause + phone location), lap distance 1000 (0x03E8), 0xEE unknown, screen 3, 0x99 unknown.
    private val run = bytes(0x54, 1, 0x40, 0b1010, 0xE8, 0x03, 0xEE, 3, 0x99)

    @Test
    fun parsesTheWrapperAndExposesTheInnerFrame() {
        val item = ActivityItemCodec.parse(run, 1, 0x40)!!
        assertEquals(3, item.offset)
        assertArrayEquals(bytes(0x40, 0b1010, 0xE8, 0x03, 0xEE, 3, 0x99), ActivityItemCodec.inner(run, item))
        val s = RunMeasurementCodec.parse(ActivityItemCodec.inner(run, item))!!
        assertEquals(RunMeasurementCodec.Setting(true, false, true, 1000, 3), s)
    }

    @Test
    fun anAnswerThatEchoesTheLeadingZeroIsRecognisedWithTheShiftedOffset() {
        val echoed = bytes(0x54, 0, 1, 0x40, 0b1010, 0xE8, 0x03, 0xEE, 3, 0x99)
        val item = ActivityItemCodec.parse(echoed, 1, 0x40)!!
        assertEquals(4, item.offset)
        assertEquals(RunMeasurementCodec.parse(ActivityItemCodec.inner(echoed, item)), RunMeasurementCodec.parse(ActivityItemCodec.inner(run, ActivityItemCodec.parse(run, 1, 0x40)!!)))
    }

    @Test
    fun anAnswerForAnotherActivityOrClassIsNotTrusted() {
        assertNull(ActivityItemCodec.parse(run, 2, 0x40))
        assertNull(ActivityItemCodec.parse(run, 1, 0x41))
        assertNull(ActivityItemCodec.parse(bytes(0x55, 1, 0x40, 1, 2), 1, 0x40))
        assertNull(ActivityItemCodec.parse(bytes(0x54, 1, 0x40), 1, 0x40))
        assertNull(ActivityItemCodec.parse(run, 0, 0x40))
    }

    @Test
    fun changingOneWrappedFlagChangesOneFrameByteOnlyAndKeepsTheUnknownBytes() {
        val item = ActivityItemCodec.parse(run, 1, 0x40)!!
        val inner = ActivityItemCodec.inner(run, item)
        val after = ActivityItemCodec.withInner(run, item, RunMeasurementCodec.withAutoRunDetection(inner, true))!!
        assertEquals(setOf(3), changed(run, after))
        assertEquals(0b1110, after[3].toInt())
        assertEquals(ActivityItemCodec.touched(item, RunMeasurementCodec.FLAG_BYTES), changed(run, after))
        assertEquals(0xEE, after[6].toInt() and 0xFF)
        assertEquals(0x99, after[8].toInt() and 0xFF)
    }

    @Test
    fun theShiftedShapeMapsTouchedBytesWithItsOffset() {
        val echoed = bytes(0x54, 0, 1, 0x40, 0b1010, 0xE8, 0x03, 0xEE, 3, 0x99)
        val item = ActivityItemCodec.parse(echoed, 1, 0x40)!!
        val after = ActivityItemCodec.withInner(echoed, item, RunMeasurementCodec.withAutoPause(ActivityItemCodec.inner(echoed, item), false))!!
        assertEquals(setOf(4), changed(echoed, after))
        assertEquals(ActivityItemCodec.touched(item, RunMeasurementCodec.FLAG_BYTES), setOf(4))
    }

    @Test
    fun aReplacementOfAnotherSizeOrClassIsRefused() {
        val item = ActivityItemCodec.parse(run, 1, 0x40)!!
        assertNull(ActivityItemCodec.withInner(run, item, bytes(0x40, 1, 2)))
        assertNull(ActivityItemCodec.withInner(run, item, bytes(0x41, 0b1010, 0xE8, 0x03, 0xEE, 3, 0x99)))
        assertNull(ActivityItemCodec.withInner(run, item, null))
    }

    @Test
    fun heartRateThroughTheWrapperKeepsTheOtherBits() {
        val frame = bytes(0x54, 2, 0x41, 0b0000_0001, 1, 110, 140)
        val item = ActivityItemCodec.parse(frame, 2, 0x41)!!
        val inner = ActivityItemCodec.inner(frame, item)
        assertEquals(HeartRateFlagsCodec.Flags(false, false, false), HeartRateFlagsCodec.parse(inner))
        val after = ActivityItemCodec.withInner(frame, item, HeartRateFlagsCodec.withZoneAlert(HeartRateSettingCodec.withMode(inner, 2)!!, true))!!
        assertEquals(0b0001_0010, after[3].toInt())
        assertEquals(setOf(3), changed(frame, after))
        assertEquals(1, after[4].toInt())
        assertEquals(110, after[5].toInt())
    }

    @Test
    fun trainingAlertsParseAndOnlyTheRepeatBytesAreWritable() {
        // index 0 = 9 (unknown), time1 300, time2 600, dist repeat 1, dist1 1000, dist2 2000, kcal repeat 2, kcal 50, alt repeat 0, alt 100
        val frame = bytes(0x54, 3, 0x55, 9, 0x2C, 0x01, 0x58, 0x02, 1, 0xE8, 0x03, 0xD0, 0x07, 2, 50, 0, 100)
        val item = ActivityItemCodec.parse(frame, 3, 0x55)!!
        val inner = ActivityItemCodec.inner(frame, item)
        val s = TrainingAlertCodec.parse(inner)!!
        assertEquals(TrainingAlertCodec.Setting(300, 600, 1, 1000, 2000, 2, 50, 0, 100), s)
        val after = ActivityItemCodec.withInner(frame, item, TrainingAlertCodec.withAltitudeRepeat(inner, 2))!!
        assertEquals(ActivityItemCodec.touched(item, TrainingAlertCodec.ALTITUDE_BYTES), changed(frame, after))
        assertNull(TrainingAlertCodec.withDistanceRepeat(inner, 3))
        assertNull(TrainingAlertCodec.parse(bytes(0x55, 9, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    @Test
    fun theActivityListAndScreensAreDecodedForReading() {
        assertEquals(listOf(1, 2, 9), ActivityListCodec.parse(bytes(0x53, 1, 2, 0, 9, 0, 0))!!)
        val screens = ActivityFaceCodec.parse(bytes(0x4E, 1, 2, 3, 7, 11, 19, 0, 5), 1)!!
        assertEquals(listOf(listOf(2, 3, 7), listOf(11, 19, 0)), screens)
        assertNull(ActivityFaceCodec.parse(bytes(0x4E, 2, 2, 3, 7), 1))
        assertEquals("Heart rate", ActivityFaceCodec.itemName(11))
        assertEquals("Running", ActivityNames.name(1))
    }

    // Activity list write: 0x53 then 15 value bytes, 0 = unused.
    private val activitiesFrame = bytes(0x53, 1, 2, 3, 6, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    @Test
    fun theActivityListRoundTripsAndKeepsItsLength() {
        assertTrue(ActivityListCodec.layoutOk(activitiesFrame))
        val enabled = ActivityListCodec.parse(activitiesFrame)!!
        assertArrayEquals(activitiesFrame, ActivityListCodec.encode(activitiesFrame, enabled)!!)
    }

    @Test
    fun disablingEnablingAndReorderingGiveTheRightBytes() {
        val off = ActivityListCodec.toggle(listOf(1, 2, 3, 6, 7), 3, false)
        assertArrayEquals(bytes(0x53, 1, 2, 6, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), ActivityListCodec.encode(activitiesFrame, off)!!)
        val on = ActivityListCodec.toggle(off, 14, true)
        assertEquals(listOf(1, 2, 6, 7, 14), on)
        assertArrayEquals(bytes(0x53, 1, 2, 6, 7, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), ActivityListCodec.encode(activitiesFrame, on)!!)
        val up = ActivityListCodec.move(listOf(1, 2, 6), 6, -1)
        assertEquals(listOf(1, 6, 2), up)
        assertEquals(listOf(2, 1, 6), ActivityListCodec.move(listOf(1, 2, 6), 1, 1))
        assertEquals(listOf(1, 2, 6), ActivityListCodec.move(listOf(1, 2, 6), 1, -1))
        assertEquals(listOf(1, 2, 6), ActivityListCodec.move(listOf(1, 2, 6), 6, 1))
        assertEquals(activitiesFrame.size, ActivityListCodec.encode(activitiesFrame, up)!!.size)
    }

    @Test
    fun anEmptyOrUnfittingListIsRefused() {
        assertNull(ActivityListCodec.encode(activitiesFrame, emptyList()))
        assertNull(ActivityListCodec.encode(activitiesFrame, listOf(1, 1)))
        assertNull(ActivityListCodec.encode(activitiesFrame, listOf(99)))
        assertNull(ActivityListCodec.encode(bytes(0x53, 1, 2), listOf(1, 2, 3)))
    }

    @Test
    fun aFrameOutsideTheNotesIsNotWritten() {
        assertTrue(!ActivityListCodec.layoutOk(bytes(0x54, 1, 2)))
        assertTrue(!ActivityListCodec.layoutOk(bytes(0x53)))
        assertTrue(!ActivityListCodec.layoutOk(ByteArray(17) { if (it == 0) 0x53 else 1 }))
        assertTrue(!ActivityListCodec.layoutOk(bytes(0x53, 1, 77)))
        assertNull(ActivityListCodec.encode(bytes(0x53, 1, 77, 0), listOf(1)))
        assertEquals("53 01 0A", ActivityListCodec.hex(bytes(0x53, 1, 10)))
    }

    // The checked write of a wrapped value, with a fake watch that answers the per-activity request.
    private class Watch(var value: ByteArray, val keeps: Boolean = true) {
        val writes = mutableListOf<ByteArray>()

        suspend fun read(): ByteArray = value.copyOf()

        suspend fun write(frame: ByteArray) {
            writes += frame.copyOf()
            if (keeps) value = frame.copyOf()
        }
    }

    @Test
    fun aWrappedWriteIsReadBackAndTheUnknownBytesSurvive() = runBlocking {
        val watch = Watch(run)
        val item = ActivityItemCodec.parse(run, 1, 0x40)!!
        val result = SafeSettingWrite({ null }, watch::write).updateWith(
            ActivityItemCodec.CODE, watch::read, ActivityItemCodec.touched(item, RunMeasurementCodec.FLAG_BYTES),
        ) { frame ->
            val it2 = ActivityItemCodec.parse(frame, 1, 0x40) ?: return@updateWith null
            ActivityItemCodec.withInner(frame, it2, RunMeasurementCodec.withPhoneLocation(ActivityItemCodec.inner(frame, it2), false))
        }
        assertEquals(SettingWriteResult.Done, result)
        assertEquals(1, watch.writes.size)
        assertEquals(0b0010, watch.value[3].toInt())
        for (i in run.indices) if (i != 3) assertEquals(run[i], watch.value[i])
    }

    @Test
    fun aWrappedWriteTheWatchDoesNotKeepIsReportedOnce() = runBlocking {
        val watch = Watch(run, keeps = false)
        val item = ActivityItemCodec.parse(run, 1, 0x40)!!
        val result = SafeSettingWrite({ null }, watch::write).updateWith(
            ActivityItemCodec.CODE, watch::read, ActivityItemCodec.touched(item, RunMeasurementCodec.FLAG_BYTES),
        ) { frame -> ActivityItemCodec.withInner(frame, item, RunMeasurementCodec.withAutoPause(ActivityItemCodec.inner(frame, item), false)) }
        assertTrue(result is SettingWriteResult.Failed)
        assertEquals(1, watch.writes.size)
        assertNotNull(watch.value)
    }

    @Test
    fun aWrappedChangeThatTouchesAnotherByteIsStopped() = runBlocking {
        val watch = Watch(run)
        val result = SafeSettingWrite({ null }, watch::write).updateWith(ActivityItemCodec.CODE, watch::read, setOf(3)) { frame ->
            frame.copyOf().also { it[3] = 1; it[5] = 0 }
        }
        assertTrue(result is SettingWriteResult.Failed)
        assertTrue(watch.writes.isEmpty())
    }

    @Test
    fun theH5600OffersOnlyTheFourActivitiesOfItsManual() {
        assertEquals(listOf(1, 2, 6, 14), ActivityModels.offered(ActivityModels.H5600))
        assertEquals(
            listOf("Running", "Walking", "Gym workout", "Interval timer"),
            ActivityModels.offered(ActivityModels.H5600).map { ActivityModels.name(ActivityModels.H5600, it) },
        )
        assertEquals(ActivityListCodec.ACTIVITIES, ActivityModels.offered("other model"))
    }

    @Test
    fun otherCodesTheWatchReportsAreHiddenButKeptInTheWrittenList() {
        val reported = listOf(1, 3, 2, 9, 14)
        assertEquals(listOf(1, 2, 14), ActivityModels.visible(ActivityModels.H5600, reported))
        val frame = bytes(0x53, 1, 3, 2, 9, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val off = ActivityListCodec.toggle(reported, 2, false)
        assertArrayEquals(bytes(0x53, 1, 3, 9, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), ActivityListCodec.encode(frame, off)!!)
    }

    @Test
    fun movingStepsOverHiddenActivitiesAndLeavesThemInPlace() {
        val visible = { c: Int -> c in ActivityModels.offered(ActivityModels.H5600) }
        assertEquals(listOf(3, 2, 1, 9, 14), ActivityListCodec.move(listOf(1, 3, 2, 9, 14), 1, 1, visible))
        assertEquals(listOf(1, 3, 14, 2, 9), ActivityListCodec.move(listOf(1, 3, 2, 9, 14), 14, -1, visible))
        assertEquals(listOf(1, 3, 2, 9, 14), ActivityListCodec.move(listOf(1, 3, 2, 9, 14), 14, 1, visible))
    }
}
