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

    /** Every sensor reads a value of a kind the plugin declared, and every write it makes is declared. */
    @Test fun theDeclarationCoversWhatThePluginUses() {
        val access = HomeAssistantPlugin().access
        HaSensors.variablesOf(HaSensors.ALL).forEach { assertTrue(it, DataUse.ofValueKey(it) in access.reads) }
        assertTrue(access.controls.containsAll(setOf(Control.NOTIFICATIONS, Control.WATCH_SETTINGS, Control.WATCH_FACE, Control.BUTTON_PRESS)))
        assertEquals(listOf("your Home Assistant server"), access.sendsTo)
    }
}
