package org.avmedia.gshockapi.io

import com.google.gson.Gson
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.model.SmartAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsIoTest {

    @Test
    fun settingsSerializesH5600PropertiesToJson() {
        val settings = Settings(
            soundMode = "MUTE",
            watchFaceNumber = 1,
            watchFaceItem = "STEPS",
            smartAlarm = SmartAlarm(7, 30, enabled = true, snooze = true, windowMin = 30)
        )

        val json = Gson().toJson(settings)

        val deserialized = Gson().fromJson(json, Settings::class.java)

        assertEquals("MUTE", deserialized.soundMode)
        assertEquals(1, deserialized.watchFaceNumber)
        assertEquals("STEPS", deserialized.watchFaceItem)
        assertNotNull(deserialized.smartAlarm)
        assertEquals(7, deserialized.smartAlarm?.hour)
        assertEquals(30, deserialized.smartAlarm?.minute)
        assertEquals(true, deserialized.smartAlarm?.enabled)
        assertEquals(true, deserialized.smartAlarm?.snooze)
        assertEquals(30, deserialized.smartAlarm?.windowMin)
    }
}
