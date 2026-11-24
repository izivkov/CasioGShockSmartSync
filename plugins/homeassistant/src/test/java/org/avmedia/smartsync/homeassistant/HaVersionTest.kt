package org.avmedia.smartsync.homeassistant

import java.io.File
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaVersionTest {
    @Test fun theBuildFileAndThePluginNameTheSameVersion() {
        val declared = Regex("""(?m)^version = '([^']+)'""").find(File("build.gradle").readText())?.groupValues?.get(1)
        assertEquals(HomeAssistantPlugin.VERSION, declared)
    }

    /**
     * Every write it makes is declared; it reads every kind but the phone's location (the host states each value's kind:
     * the app's HaDeclarationCoversItsSensorsTest checks every sensor against it).
     */
    @Test fun theDeclarationCoversWhatThePluginUses() {
        val access = HomeAssistantPlugin().access
        assertEquals(DataUse.entries.toSet() - DataUse.PHONE_LOCATION, access.reads)
        assertTrue(access.controls.containsAll(setOf(Control.NOTIFICATIONS, Control.WATCH_SETTINGS, Control.WATCH_FACE, Control.BUTTON_PRESS)))
        assertEquals(listOf("your Home Assistant server"), access.sendsTo)
    }
}
