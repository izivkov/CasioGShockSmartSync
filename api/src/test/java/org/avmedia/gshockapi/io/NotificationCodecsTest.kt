package org.avmedia.gshockapi.io

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0x42 and 0x3D frames built from the documented layouts (frame byte = value index + 1). */
class NotificationCodecsTest {
    private fun frame(code: Int, vararg values: Int) = byteArrayOf(code.toByte()) + values.map { it.toByte() }.toByteArray()

    private fun changed(a: ByteArray, b: ByteArray) = a.indices.filter { a[it] != b[it] }.toSet()

    // 0x42: flags 0b1001 = sound mode 1, vibration mode 2; sound mask 0x0105, vibration mask 0x4002, pattern 2, extra byte 0x77.
    private val sounds = frame(0x42, 0b1001, 0x05, 0x01, 0x02, 0x40, 2, 0x77)

    @Test
    fun parsesModesMasksAndPattern() {
        val s = NotificationSoundCodec.parse(sounds)!!
        assertEquals(1, s.soundMode)
        assertEquals(2, s.vibrationMode)
        assertEquals(0x0105, s.soundMask)
        assertEquals(0x4002, s.vibrationMask)
        assertEquals(2, s.pattern)
        assertTrue(s.soundOn(0) && s.soundOn(2) && s.soundOn(8) && !s.soundOn(1))
        assertTrue(s.vibrationOn(1) && s.vibrationOn(14) && !s.vibrationOn(0))
    }

    @Test
    fun theSoundModeChangesOnlyBitsZeroAndOneOfIndexZero() {
        val flags = frame(0x42, 0b1111_1001, 0, 0, 0, 0, 1)
        val after = NotificationSoundCodec.withSoundMode(flags, 2)!!
        assertEquals(0b1111_1010, after[1].toInt() and 0xFF)
        assertEquals(NotificationSoundCodec.MODE_BYTES, changed(flags, after))
    }

    @Test
    fun theVibrationModeChangesOnlyBitsTwoAndThree() {
        val after = NotificationSoundCodec.withVibrationMode(sounds, 0)!!
        assertEquals(0b0001, after[1].toInt())
        assertEquals(NotificationSoundCodec.MODE_BYTES, changed(sounds, after))
    }

    @Test
    fun oneSoundEventChangesOneBitOfTheMask() {
        val on = NotificationSoundCodec.withSoundEvent(sounds, 9, true)!!
        assertEquals(0x0305, NotificationSoundCodec.parse(on)!!.soundMask)
        assertEquals(setOf(3), changed(sounds, on))
        val off = NotificationSoundCodec.withSoundEvent(sounds, 0, false)!!
        assertEquals(0x0104, NotificationSoundCodec.parse(off)!!.soundMask)
        assertEquals(NotificationSoundCodec.SOUND_MASK_BYTES.containsAll(changed(sounds, off)), true)
    }

    @Test
    fun oneVibrationEventLeavesTheSoundMaskAlone() {
        val after = NotificationSoundCodec.withVibrationEvent(sounds, 14, false)!!
        assertEquals(0x0002, NotificationSoundCodec.parse(after)!!.vibrationMask)
        assertEquals(0x0105, NotificationSoundCodec.parse(after)!!.soundMask)
        assertTrue(NotificationSoundCodec.VIBRATION_MASK_BYTES.containsAll(changed(sounds, after)))
    }

    @Test
    fun thePatternChangesOnlyIndexFiveAndTheExtraByteStays() {
        val after = NotificationSoundCodec.withPattern(sounds, 3)!!
        assertEquals(NotificationSoundCodec.PATTERN_BYTES, changed(sounds, after))
        assertEquals(0x77, after[7].toInt())
    }

    @Test
    fun badInputIsRefused() {
        assertNull(NotificationSoundCodec.parse(frame(0x42, 3, 0, 0, 0, 0, 1)))
        assertNull(NotificationSoundCodec.parse(frame(0x42, 0, 0, 0, 0, 0, 4)))
        assertNull(NotificationSoundCodec.parse(frame(0x42, 0, 0, 0, 0))) // 4 value bytes is too short; 5 is the real answer of a GBX-H5600
        assertNull(NotificationSoundCodec.parse(frame(0x41, 0, 0, 0, 0, 0, 1)))
        assertNull(NotificationSoundCodec.withSoundMode(sounds, 3))
        assertNull(NotificationSoundCodec.withSoundEvent(sounds, 15, true))
        assertNull(NotificationSoundCodec.withPattern(sounds, 4))
        assertEquals(15, NotificationSoundCodec.EVENTS.size)
    }

    // 0x3D: command 0x30 as a read returns it; language 2; off at night 1; pop-up 1; unknown bytes 0xA5 and 0x5A.
    private fun manager(cmd: Int = 0x30, lang: Int = 0b1111_1110, night: Int = 0b1111_1010, popUp: Int = 0xF1) =
        frame(0x3D, cmd, lang, 0xA5, 0, 0, 0, night, 0, 0, 0, 0, 0, 0, 0x5A, 0, 0, popUp)

    @Test
    fun parsesLanguageOffAtNightAndPopUp() {
        val s = NotificationManagerCodec.parse(manager())!!
        assertEquals(NotificationManagerCodec.Setting(2, 1, 1), s)
    }

    @Test
    fun aLanguageChangeSetsCommand31AndTouchesOnlyItsBits() {
        val before = manager()
        val after = NotificationManagerCodec.withLanguage(before, 3)!!
        assertEquals(0x31, after[1].toInt())
        assertEquals(0b1111_1111, after[2].toInt() and 0xFF)
        assertEquals(NotificationManagerCodec.LANGUAGE_BYTES, changed(before, after))
        assertEquals(3, NotificationManagerCodec.parse(after)!!.language)
        assertEquals(1, NotificationManagerCodec.parse(after)!!.offAtNight)
    }

    @Test
    fun anOffAtNightChangeSetsCommand32AndTouchesOnlyBitsOneAndTwo() {
        val before = manager()
        val after = NotificationManagerCodec.withOffAtNight(before, 2)!!
        assertEquals(0x32, after[1].toInt())
        assertEquals(0b1111_1100, after[7].toInt() and 0xFF)
        assertEquals(NotificationManagerCodec.NIGHT_BYTES, changed(before, after))
    }

    @Test
    fun aValueTheWatchAlreadyHasIsNotAChangeAndKeepsTheReadCommand() {
        val before = manager()
        assertArrayEquals(before, NotificationManagerCodec.withLanguage(before, 2))
        assertArrayEquals(before, NotificationManagerCodec.withOffAtNight(before, 1))
    }

    @Test
    fun aShortOrForeignManagerFrameIsRefused() {
        assertNull(NotificationManagerCodec.parse(frame(0x3D, 0x30, 1, 2, 3)))
        assertNull(NotificationManagerCodec.parse(frame(0x42, *IntArray(17))))
        assertNull(NotificationManagerCodec.withLanguage(manager(), 4))
        assertFalse(NotificationManagerCodec.withLanguage(frame(0x3D, 1), 1) != null)
    }

    // The checked write, with a fake watch that reports command 0x30 on every read, as the watch does.
    private class Watch(var value: ByteArray) {
        val writes = mutableListOf<ByteArray>()

        suspend fun read(code: Int): ByteArray = value.copyOf()

        suspend fun write(frame: ByteArray) {
            writes += frame.copyOf()
            value = frame.copyOf().also { it[1] = 0x30 }
        }
    }

    @Test
    fun theManagerWriteIsReadBackAndEveryOtherByteIsKept() = runBlocking {
        val watch = Watch(manager())
        val original = manager()
        val result = SafeSettingWrite(watch::read, watch::write).update(
            NotificationManagerCodec.CODE, NotificationManagerCodec.NIGHT_BYTES, NotificationManagerCodec::expectedBack,
        ) { NotificationManagerCodec.withOffAtNight(it, 3) }
        assertEquals(SettingWriteResult.Done, result)
        assertEquals(1, watch.writes.size)
        assertEquals(0x32, watch.writes[0][1].toInt())
        for (i in original.indices) if (i !in NotificationManagerCodec.NIGHT_BYTES) assertEquals(original[i], watch.value[i])
    }

    @Test
    fun anUnchangedManagerValueWritesNothing() = runBlocking {
        val watch = Watch(manager())
        val result = SafeSettingWrite(watch::read, watch::write).update(
            NotificationManagerCodec.CODE, NotificationManagerCodec.LANGUAGE_BYTES, NotificationManagerCodec::expectedBack,
        ) { NotificationManagerCodec.withLanguage(it, 2) }
        assertEquals(SettingWriteResult.Unchanged, result)
        assertTrue(watch.writes.isEmpty())
    }

    // A GBX-H5600, exact answers.
    @Test
    fun theRealSoundAnswerOfFiveValueBytesParsesWithoutAPatternAndIsWrittenBackAsFive() {
        val real = byteArrayOf(0x42, 0x0A, 0xFF.toByte(), 0x7F, 0xFF.toByte(), 0x7F)
        val s = NotificationSoundCodec.parse(real)!!
        assertEquals(2, s.soundMode)
        assertEquals(2, s.vibrationMode)
        assertEquals(0x7FFF, s.soundMask)
        assertEquals(0x7FFF, s.vibrationMask)
        assertEquals(null, s.pattern)
        assertEquals(null, NotificationSoundCodec.withPattern(real, 1))
        assertEquals(6, NotificationSoundCodec.withSoundMode(real, 0)!!.size)
    }

    @Test
    fun theRealNotificationManagerAnswerParses() {
        val real = "3D 30 00 02 01 00 03 00 2F 28 00 28 00 28 00 FA 00 00".split(' ').map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(true, NotificationManagerCodec.parse(real) != null)
        assertEquals(true, NotificationManagerCodec.parse(real.copyOf(17)) != null)
    }
}
