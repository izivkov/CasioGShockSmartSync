package org.avmedia.gshockGoogleSync.homeassistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every registry entry goes through its real codec: a value written is the value read back, and nothing else changes. */
class WatchSettingRegistryTest {
    private fun frame(code: Int, vararg v: Int) = byteArrayOf(code.toByte()) + v.map { it.toByte() }.toByteArray()

    /** One plausible frame per code (the layouts of the codec tests). */
    private val samples: Map<Int, ByteArray> = mapOf(
        0x51 to frame(0x51, 2, 0, 10, 12, 19, 0x77),
        0x41 to frame(0x41, 0b0001_0110, 2, 120, 130, 0x55),
        0x4F to frame(0x4F, 3, 23, 15, 6, 45, 1, 190, 60, 45, 0x77),
        0x13 to frame(0x13, 0x01, 0x21, 0, 1, 0, 0, 0, 0, 0, 3, 0, 0b0010_0100, 22, 30, 6, 15, 0),
        0x43 to frame(0x43, 0x10, 0x27, 0x00, 9, 9, 7, 7, 7, 1, 1, 1, 1, 1, 0, 5, 1),
        0x45 to frame(0x45, 0x00, 0x97, 0xFE, 0x9B, 0xFF, 0x6F, 0xE6, 0xF8, 0xF6, 0xAA),
        0x50 to frame(0x50, 6, 30, 0b01, 30),
        0x42 to frame(0x42, 0b1001, 0x05, 0x01, 0x02, 0x40, 2, 0x77),
        // A GBX-H5600: 3D 30, language 0 (index 1), off at night 0 (index 6), pop-up byte at index 16.
        0x3D to frame(0x3D, 0x30, 0x00, 0x02, 0x01, 0x00, 0x03, 0x00, 0x2F, 0x28, 0x00, 0x28, 0x00, 0x28, 0x00, 0xFA, 0x00, 0x00),
        0x53 to frame(0x53, 1, 2, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x77),
    )
    private val frames: Map<String, ByteArray> = samples.mapKeys { RawSettings.name(it.key) }

    private fun valuesOf(s: WatchSetting): List<Any> = when (val t = s.type) {
        is SettingType.Choice -> t.values
        is SettingType.Whole -> (t.only ?: setOf(t.range.first, (t.range.first + t.range.last) / 2, t.range.last)).toList()
        SettingType.Bool -> listOf(true, false)
        SettingType.Time -> listOf("00:00", "07:30", "23:59")
    }

    @Test fun everyEntryRoundTripsThroughItsCodecAndTouchesOnlyItsBytes() {
        for (s in WatchSettingRegistry.ALL) {
            val before = samples.getValue(s.code)
            assertNotNull("${s.key}: the sample is not understood", s.read(before))
            for (v in valuesOf(s)) {
                val checked = WatchSettingRegistry.check(s, v)
                assertTrue("${s.key}=$v refused", checked is SettingCheck.Ok)
                val canonical = (checked as SettingCheck.Ok).value
                val after = s.change(before, canonical)
                assertNotNull("${s.key}=$v not written", after)
                assertEquals("${s.key}=$v read back", canonical, s.read(after!!))
                for (i in before.indices) if (i !in s.touched) assertEquals("${s.key}=$v changed byte $i", before[i], after[i])
            }
        }
    }

    @Test fun theRemainingKeysExist() {
        for (k in listOf("notification_language", "notification_off_at_night", "activity_running", "activity_walking", "activity_gym_workout", "activity_interval_timer"))
            assertNotNull(k, WatchSettingRegistry.find(k))
        assertNull(WatchSettingRegistry.find("activity_pool"))
    }

    @Test fun anOutOfRangeLanguageIsRefusedWithAReason() {
        assertTrue(refused("notification_language", 4).contains("0 to 3"))
        assertTrue(refused("notification_off_at_night", -1).contains("0 to 3"))
        assertTrue(refused("activity_running", "maybe").contains("true"))
    }

    @Test fun theLastActivityCannotBeSwitchedOff() {
        val only = frame(0x53, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x77)
        assertNull(WatchSettingRegistry.find("activity_running")!!.change(only, false))
    }

    @Test fun switchingAnActivityOnAppendsItAndKeepsTheTail() {
        val after = WatchSettingRegistry.find("activity_interval_timer")!!.change(samples.getValue(0x53), true)!!
        assertEquals(listOf(1, 2, 6, 14), org.avmedia.gshockapi.io.ActivityListCodec.parse(after))
        assertEquals(0x77.toByte(), after[15])
    }

    @Test fun theNotificationManagerReadBackIgnoresTheCommandByte() {
        val s = WatchSettingRegistry.find("notification_language")!!
        val before = samples.getValue(0x3D)
        val written = s.change(before, 3)!!
        assertEquals(0x31.toByte(), written[1])
        assertTrue(s.matches(before.copyOf().also { it[2] = 3 }, s.expectedBack(before, written)))
    }

    @Test fun theRegistryReadsOnlyCodesTheSyncReads() {
        assertTrue(RawSettings.DECODED_CODES.containsAll(WatchSettingRegistry.CODES))
    }

    @Test fun unknownKeyIsNotInTheRegistry() {
        assertNull(WatchSettingRegistry.find("time"))
        assertNull(WatchSettingRegistry.find("pairing"))
    }

    private fun refused(key: String, raw: Any?): String =
        (WatchSettingRegistry.check(WatchSettingRegistry.find(key)!!, raw) as SettingCheck.Refused).reason

    @Test fun badValuesAreRefusedWithAReason() {
        assertTrue(refused("step_goal", 99).contains("100"))
        assertTrue(refused("step_goal", 200_001).contains("200000"))
        assertTrue(refused("step_goal", 5.5).contains("whole"))
        assertTrue(refused("watch_face", "moon").contains("steps"))
        assertTrue(refused("bedtime", "25:00").contains("07:30"))
        assertTrue(refused("smart_alarm", "maybe").contains("true"))
        assertTrue(refused("smart_alarm_window_min", 20).contains("15"))
        assertTrue(refused("custom_slot", 7).contains("allowed"))
    }

    @Test fun valuesAreAcceptedInTheWordsPeopleWrite() {
        fun ok(key: String, raw: Any?) = (WatchSettingRegistry.check(WatchSettingRegistry.find(key)!!, raw) as SettingCheck.Ok).value
        assertEquals("time_only", ok("watch_face", "Time only"))
        assertEquals("steps", ok("watch_face", " STEPS "))
        assertEquals(true, ok("smart_alarm", "on"))
        assertEquals(10_000, ok("step_goal", "10000"))
        assertEquals(8_000, ok("step_goal", 8000.0))
        assertEquals("07:05", ok("bedtime", "7:05"))
    }

    @Test fun theFaceChangesOnlyTheFaceByte() {
        val s = WatchSettingRegistry.find("watch_face")!!
        assertArrayEquals(byteArrayOf(0x51, 3, 0, 10, 12, 19, 0x77), s.change(samples.getValue(0x51), "steps"))
    }

    @Test fun theSettingsSensorHoldsEveryRegistryValue() {
        val values = WatchSettingVariables.DEFS.associate { it.name to it.value(frames) }
        val sensors = HaSensors.ALL
        val attrs = HaSensors.attributes(values, sensors).getValue("watch_writable_settings")
        for (s in WatchSettingRegistry.ALL) assertEquals(s.key, s.valueIn(frames), attrs[s.key])
        assertEquals(WatchSettingRegistry.ALL.size, attrs.size)
        assertEquals(WatchSettingRegistry.ALL.size, HaSensors.states(values, sensors)["watch_writable_settings"])
        assertEquals("custom", HaSensors.states(values, sensors)["watch_face_name"])
        assertEquals("sleep_score_and_level", HaSensors.states(values, sensors)["custom_slot"])
    }

    @Test fun theWritableSettingsAreInTheSettingsGroup() {
        assertEquals(HaGroup.SETTINGS, HaGroups.of(HaSensors.ALL.first { it.uniqueId == "watch_writable_settings" }))
    }
}
