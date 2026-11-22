package org.avmedia.gshockapi.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setting frames built from the documented layouts (frame byte = value index + 1). */
class WatchSettingCodecsTest {
    private fun frame(code: Int, vararg values: Int) = byteArrayOf(code.toByte()) + values.map { it.toByte() }.toByteArray()

    /** Indexes where two frames differ, as frame positions. */
    private fun changed(a: ByteArray, b: ByteArray) = a.indices.filter { a[it] != b[it] }.toSet()

    // 0x51 watch face

    @Test
    fun parsesTheFaceAndTheFourItems() {
        val face = WatchFaceCodec.parse(frame(0x51, 2, 0x00, 0x0A, 0x0C, 0x13, 0x77))
        assertEquals(WatchFaceCodec.Face(2, listOf(0x00, 0x0A, 0x0C, 0x13)), face)
    }

    @Test
    fun changingOneSlotChangesOnlyThatByte() {
        val before = frame(0x51, 2, 0, 10, 12, 19, 0x77, 0x88)
        val after = WatchFaceCodec.withItem(before, 3, 6)!!
        assertEquals(setOf(4), changed(before, after))
        assertEquals(6, after[4].toInt())
        assertEquals(WatchFaceCodec.itemBytes(3), changed(before, after))
    }

    @Test
    fun changingTheFaceChangesOnlyTheFaceByte() {
        val before = frame(0x51, 2, 0, 10, 12, 19, 0x77)
        val after = WatchFaceCodec.withFace(before, 1)!!
        assertEquals(WatchFaceCodec.FACE_BYTES, changed(before, after))
    }

    @Test
    fun aShortOrForeignFrameIsRefused() {
        assertNull(WatchFaceCodec.parse(frame(0x51, 1, 2, 3)))
        assertNull(WatchFaceCodec.parse(frame(0x52, 1, 2, 3, 4, 5)))
        assertNull(WatchFaceCodec.withItem(frame(0x51, 2, 0, 10, 12, 19), 5, 1))
        assertNull(WatchFaceCodec.withItem(frame(0x51, 2, 0, 10, 12, 19), 1, 20))
    }

    @Test
    fun theItemTableHasTwentyNamesAndTheUnnamedOneIsItem15() {
        assertEquals(20, WatchFaceCodec.ITEM_NAMES.size)
        assertEquals("Steps", WatchFaceCodec.itemName(0))
        assertEquals("Item 15", WatchFaceCodec.itemName(0x0F))
        assertEquals("Moon age", WatchFaceCodec.itemName(0x13))
    }

    // 0x41 heart rate measurement

    @Test
    fun parsesAndChangesTheHeartRateMode() {
        val before = frame(0x41, 0b0001_0110, 2, 120, 130, 0x55)
        assertEquals(HeartRateSettingCodec.Setting(2, 2, 120, 130), HeartRateSettingCodec.parse(before))
        val after = HeartRateSettingCodec.withMode(before, 1)!!
        assertEquals(0b0001_0101, after[1].toInt())
        assertEquals(HeartRateSettingCodec.MODE_BYTES, changed(before, after))
    }

    @Test
    fun aHeartRateModeAboveTwoIsRefused() {
        assertNull(HeartRateSettingCodec.withMode(frame(0x41, 0, 0, 120, 130), 3))
        assertNull(HeartRateSettingCodec.parse(frame(0x41, 3, 0, 120, 130)))
    }

    // 0x4F bedtime and wake-up

    @Test
    fun parsesBedtimeAndWakeUp() {
        val sleep = PhysicalSettingsCodec.parse(frame(0x4F, 3, 23, 15, 6, 45, 1, 190, 60, 45))!!
        assertEquals(23 to 15, sleep.bedtime)
        assertEquals(6 to 45, sleep.wakeUp)
    }

    @Test
    fun changingBedtimeLeavesWakeUpAndTheRestAlone() {
        val before = frame(0x4F, 3, 23, 15, 6, 45, 1, 190, 60, 45)
        val after = PhysicalSettingsCodec.withBedtime(before, 22, 30)!!
        assertEquals(PhysicalSettingsCodec.BEDTIME_BYTES, changed(before, after))
        assertEquals(22 to 30, PhysicalSettingsCodec.parse(after)!!.bedtime)
        assertEquals(6 to 45, PhysicalSettingsCodec.parse(after)!!.wakeUp)
        val wake = PhysicalSettingsCodec.withWakeUp(before, 7, 0)!!
        assertEquals(PhysicalSettingsCodec.WAKE_BYTES, changed(before, wake))
    }

    @Test
    fun anImpossibleTimeIsRefused() {
        val before = frame(0x4F, 3, 23, 15, 6, 45, 1, 190, 60, 45)
        assertNull(PhysicalSettingsCodec.withBedtime(before, 24, 0))
        assertNull(PhysicalSettingsCodec.withWakeUp(before, 6, 60))
        assertNull(PhysicalSettingsCodec.parse(frame(0x4F, 3, 99, 15, 6, 45)))
    }

    // 0x13 fields

    private fun basic(flags: Int = 0b0010_0100) =
        frame(0x13, 0x01, 0x02, 0, 1, 0, 0, 0, 0, 0, 3, 0, flags, 22, 30, 6, 15, 0)

    @Test
    fun parsesTheStepReminderUnitAndNightWindow() {
        val fields = BasicHealthFieldsCodec.parse(basic(0b0000_0010))!!
        assertEquals(true, fields.stepReminder)
        assertEquals(LengthUnit.KM, fields.unit)
        assertEquals(22 to 30, fields.nightStart)
        assertEquals(6 to 15, fields.nightEnd)
    }

    @Test
    fun theStepReminderChangesOnlyItsBit() {
        val before = basic(0b0010_0100)
        val on = BasicHealthFieldsCodec.withStepReminder(before, true)!!
        assertEquals(0b0010_0110, on[12].toInt())
        assertEquals(BasicHealthFieldsCodec.FLAGS_BYTES, changed(before, on))
        val off = BasicHealthFieldsCodec.withStepReminder(on, false)!!
        assertArrayEquals(before, off)
    }

    @Test
    fun theUnitChangesOnlyItsBit() {
        val before = basic(0b0010_0110)
        val after = BasicHealthFieldsCodec.withUnit(before, LengthUnit.MILES)!!
        assertEquals(0b0010_0111, after[12].toInt())
        assertEquals(BasicHealthFieldsCodec.FLAGS_BYTES, changed(before, after))
    }

    @Test
    fun theUnitIsAChoiceOfTwoNamedValues() {
        assertEquals(listOf("Kilometres", "Miles"), LengthUnit.entries.map { it.label })
        assertEquals(LengthUnit.MILES, BasicHealthFieldsCodec.parse(basic(0b0000_0001))!!.unit)
        val back = BasicHealthFieldsCodec.withUnit(basic(0b0000_0001), LengthUnit.KM)!!
        assertEquals(0b0000_0000, back[12].toInt())
    }

    @Test
    fun theNightWindowChangesOnlyItsFourBytes() {
        val before = basic()
        val after = BasicHealthFieldsCodec.withNightWindow(before, 23 to 0, 7 to 30)!!
        assertEquals(BasicHealthFieldsCodec.NIGHT_BYTES, changed(before, after))
        assertEquals(23 to 0, BasicHealthFieldsCodec.parse(after)!!.nightStart)
        assertEquals(7 to 30, BasicHealthFieldsCodec.parse(after)!!.nightEnd)
        assertNull(BasicHealthFieldsCodec.withNightWindow(before, 25 to 0, 7 to 30))
    }

    @Test
    fun aShortBasicFrameIsRefused() {
        assertNull(BasicHealthFieldsCodec.parse(frame(0x13, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
    }

    // 0x43 step goal

    @Test
    fun theStepGoalIsThreeBytesLowByteFirstAndOthersStay() {
        val before = frame(0x43, 0x10, 0x27, 0x00, 9, 9, 7, 7, 7, 1, 1, 1, 1, 1, 0, 5, 1)
        assertEquals(10_000, StepGoalCodec.parse(before))
        val after = StepGoalCodec.withSteps(before, 12_345)!!
        assertEquals(12_345, StepGoalCodec.parse(after))
        // 0x00 stays 0x00: only the first two bytes differ, and nothing outside the three changes
        assertEquals(setOf(1, 2), changed(before, after))
        assertTrue(StepGoalCodec.BYTES.containsAll(changed(before, after)))
        assertArrayEquals(byteArrayOf(0x39, 0x30, 0x00), after.copyOfRange(1, 4))
    }

    @Test
    fun aStepGoalOutsideTheLimitsIsRefused() {
        val before = frame(0x43, 0x10, 0x27, 0x00, 9)
        assertNull(StepGoalCodec.withSteps(before, 50))
        assertNull(StepGoalCodec.withSteps(before, 300_000))
    }

    // 0x45 profile

    private fun profile(vararg v: Int) = frame(0x45, *v)

    @Test
    fun readsTheProfileWithTheLowByteFirst() {
        // height 175 = 75 01, weight 82 = 82 00, year 1985 = 85 19, month 3, day 14
        val p = ProfileCodec.parse(profile(0xFF, 0x75, 0x01, 0x82, 0x00, 0x85, 0x19, 0x03, 0x14))!!
        assertEquals(175, p.heightCm)
        assertEquals(82, p.weightKg)
        assertEquals(1985, p.birthYear)
        assertEquals(3, p.birthMonth)
        assertEquals(14, p.birthDay)
        assertEquals(true, p.lowByteFirst)
    }

    @Test
    fun readsTheProfileWithTheHighByteFirstWhenOnlyThatIsPlausible() {
        val p = ProfileCodec.parse(profile(0xFF, 0x01, 0x75, 0x00, 0x82, 0x19, 0x85, 0x03, 0x14))!!
        assertEquals(175, p.heightCm)
        assertEquals(82, p.weightKg)
        assertEquals(false, p.lowByteFirst)
    }

    @Test
    fun aProfileWriteChangesOnlyHeightAndWeight() {
        val before = profile(0xFF, 0x75, 0x01, 0x82, 0x00, 0x85, 0x19, 0x03, 0x14, 0xAA)
        val after = ProfileCodec.withHeightAndWeight(before, 180, 79)!!
        assertTrue(ProfileCodec.BYTES.containsAll(changed(before, after)))
        val p = ProfileCodec.parse(after)!!
        assertEquals(180, p.heightCm)
        assertEquals(79, p.weightKg)
        assertEquals(1985, p.birthYear)
        assertEquals(0xAA, after[10].toInt() and 0xFF)
    }

    @Test
    fun anUnrecognisedProfileIsNeverWritten() {
        val odd = profile(0xFF, 0x12, 0x34, 0x56, 0x78, 0x85, 0x19, 0x03, 0x14)
        assertNull(ProfileCodec.parse(odd))
        assertNull(ProfileCodec.withHeightAndWeight(odd, 180, 79))
        assertNotNull(ProfileCodec.parse(profile(0xFF, 0x75, 0x01, 0x82, 0x00, 0x85, 0x19, 0x03, 0x14)))
    }

    // 0x50 smart alarm

    @Test
    fun readsTheSmartAlarm() {
        val s = SmartAlarmCodec.parse(frame(0x50, 6, 30, 0b11, 30))!!
        assertEquals(SmartAlarmCodec.Setting(6, 30, true, true, 30), s)
        assertNull(SmartAlarmCodec.parse(frame(0x50, 6, 30)))
        // the two flag bits are told apart: bit 0 is on, bit 1 is snooze
        assertEquals(SmartAlarmCodec.Setting(7, 0, true, false, 45), SmartAlarmCodec.parse(frame(0x50, 7, 0, 0b01, 45)))
        assertEquals(SmartAlarmCodec.Setting(7, 0, false, true, 15), SmartAlarmCodec.parse(frame(0x50, 7, 0, 0b10, 15)))
    }

    @Test
    fun onlyFacesOneToFourAreWritten() {
        val before = frame(0x51, 2, 0, 10, 12, 19, 0x77)
        assertNull(WatchFaceCodec.withFace(before, 0))
        assertNull(WatchFaceCodec.withFace(before, 5))
        assertNull(WatchFaceCodec.withFace(before, -1))
        assertNull(WatchFaceCodec.withFace(before, 256))
        for (f in 1..4) assertEquals(f.toByte(), WatchFaceCodec.withFace(before, f)!![1])
        assertEquals(listOf("Time only", "Custom", "Steps", "Tide"), WatchFaceCodec.FACES.map { WatchFaceCodec.faceName(it) })
    }

    @Test
    fun theRawFaceIndexStillWorksForDecoding() {
        assertEquals(7.toByte(), WatchFaceCodec.withFaceRaw(frame(0x51, 2, 0, 10, 12, 19, 0x77), 7)!![1])
    }

    @Test
    fun aTopRightWriteChangesOnlySlotThreesByte() {
        val before = frame(0x51, 2, 0, 10, 12, 19, 0x77)
        val after = WatchFaceCodec.withTopRight(before, 6)!!
        assertEquals(setOf(4), changed(before, after))
        assertEquals(WatchFaceCodec.TOP_RIGHT_BYTES, changed(before, after))
        assertEquals(6.toByte(), after[4])
        // The bytes of slots 1, 2 and 4, the face and the trailing byte are kept.
        assertEquals(listOf<Byte>(0x51, 2, 0, 10, 6, 19, 0x77), after.toList())
        assertNull(WatchFaceCodec.withTopRight(before, 20))
    }

    // 0x45 birth date and 0x4F body values

    @Test
    fun aBirthDateWriteChangesOnlyTheDateBytes() {
        val before = profile(0xFF, 0x75, 0x01, 0x82, 0x00, 0x85, 0x19, 0x03, 0x14, 0xAA)
        val after = ProfileCodec.withBirthDate(before, 1990, 7, 9)!!
        assertTrue(ProfileCodec.BIRTH_BYTES.containsAll(changed(before, after)))
        val p = ProfileCodec.parse(after)!!
        assertEquals(listOf(1990, 7, 9), listOf(p.birthYear, p.birthMonth, p.birthDay))
        assertEquals(175, p.heightCm)
        assertEquals(0xAA.toByte(), after[10])
    }

    @Test
    fun anImpossibleBirthDateIsRefused() {
        val before = profile(0xFF, 0x75, 0x01, 0x82, 0x00, 0x85, 0x19, 0x03, 0x14)
        assertNull(ProfileCodec.withBirthDate(before, 1990, 2, 30))
        assertNull(ProfileCodec.withBirthDate(before, 1990, 13, 1))
        assertNull(ProfileCodec.withBirthDate(before, 1850, 1, 1))
    }

    private fun body(flags: Int = 0b0000_0001) = frame(0x4F, 3, 23, 15, 6, 45, flags, 190, 60, 45, 0x77)

    @Test
    fun parsesTheBodyValues() {
        val b = PhysicalSettingsCodec.parseBody(body(0b0000_0101))!!
        assertEquals(PhysicalSettingsCodec.Body(3, 190, 60, 45, true, false, true), b)
        assertNull(PhysicalSettingsCodec.parseBody(frame(0x4F, 9, 23, 15, 6, 45, 0, 190, 60, 45)))
    }

    @Test
    fun eachBodyValueChangesOnlyItsByteAndItsFlag() {
        val before = body(0b0000_0001)
        val max = PhysicalSettingsCodec.withMaxHeartRate(before, 185)!!
        assertEquals(setOf(7), changed(before, max))
        assertTrue(PhysicalSettingsCodec.MAX_HR_BYTES.containsAll(changed(before, max)))
        assertEquals(185, PhysicalSettingsCodec.parseBody(max)!!.maxHr)
        val rest = PhysicalSettingsCodec.withRestingHeartRate(before, 52)!!
        assertTrue(PhysicalSettingsCodec.RESTING_HR_BYTES.containsAll(changed(before, rest)))
        assertEquals(setOf(8, 6), changed(before, rest))
        assertEquals(true, PhysicalSettingsCodec.parseBody(rest)!!.restingHrByUser)
        assertEquals(true, PhysicalSettingsCodec.parseBody(rest)!!.maxHrByUser)
        val vo2 = PhysicalSettingsCodec.withVo2max(before, 50)!!
        assertEquals(setOf(9, 6), changed(before, vo2))
        val bg = PhysicalSettingsCodec.withTrainingBackground(before, 5)!!
        assertEquals(PhysicalSettingsCodec.BACKGROUND_BYTES, changed(before, bg))
    }

    @Test
    fun bodyValuesOutsideTheirRangeAreRefused() {
        val b = body()
        assertNull(PhysicalSettingsCodec.withMaxHeartRate(b, 99))
        assertNull(PhysicalSettingsCodec.withMaxHeartRate(b, 231))
        assertNull(PhysicalSettingsCodec.withRestingHeartRate(b, 20))
        assertNull(PhysicalSettingsCodec.withVo2max(b, 5))
        assertNull(PhysicalSettingsCodec.withTrainingBackground(b, 7))
    }
}
