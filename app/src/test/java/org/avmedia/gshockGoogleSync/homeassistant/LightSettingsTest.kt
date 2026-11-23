package org.avmedia.gshockGoogleSync.homeassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** auto_light and light_duration on a real 0x13 frame: only the target bits change; bad values are refused. */
class LightSettingsTest {
    /** 0x13 as a GBX-H5600 answers it. */
    private val owner = "13 05 00 00 00 00 00 00 00 00 00 00 04 00 00 06 00".split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun setting(key: String) = WatchSettingRegistry.find(key)!!

    private fun write(key: String, value: Any, frame: ByteArray = owner): ByteArray {
        val s = setting(key)
        val ok = WatchSettingRegistry.check(s, value) as SettingCheck.Ok
        return s.change(frame, ok.value)!!
    }

    private fun changedBytes(a: ByteArray, b: ByteArray) = a.indices.filter { a[it] != b[it] }

    @Test fun bothKeysAreOnTheOneCheckedPathOf0x13() {
        for (k in listOf("auto_light", "light_duration")) {
            assertEquals(0x13, setting(k).code)
            assertTrue(0x13 in RawSettings.DECODED_CODES)
        }
    }

    @Test fun autoLightChangesOnlyBit2OfIndex0() {
        assertEquals(false, setting("auto_light").read(owner)) // bit 2 set: off
        val on = write("auto_light", true)
        assertEquals(listOf(1), changedBytes(owner, on))
        assertEquals(0x01, on[1].toInt())
        assertEquals(true, setting("auto_light").read(on))
        assertTrue(write("auto_light", false, on).contentEquals(owner))
    }

    @Test fun lightDurationChangesOnlyTheLowNibbleOfIndex1() {
        assertEquals("short", setting("light_duration").read(owner))
        val long = write("light_duration", "long")
        assertEquals(listOf(2), changedBytes(owner, long))
        assertEquals(0x01, long[2].toInt())
        val highBits = owner.copyOf().also { it[2] = 0x30 }
        assertEquals(0x31, write("light_duration", "long", highBits)[2].toInt())
        assertEquals(0x30, write("light_duration", "short", write("light_duration", "long", highBits))[2].toInt())
    }

    @Test fun anOutOfRangeDurationIsRefused() {
        val s = setting("light_duration")
        for (bad in listOf<Any>("medium", 3, "4s", true)) {
            val c = WatchSettingRegistry.check(s, bad)
            assertTrue("$bad", c is SettingCheck.Refused && c.reason.contains("short, long"))
        }
        assertNull(s.change(owner, "medium"))
        assertNull(s.read(owner.copyOf().also { it[2] = 0x05 })) // a nibble value not known here is not shown as short or long
        assertNull(WatchSettingRegistry.check(setting("auto_light"), "maybe").let { (it as? SettingCheck.Ok)?.value })
    }
}
