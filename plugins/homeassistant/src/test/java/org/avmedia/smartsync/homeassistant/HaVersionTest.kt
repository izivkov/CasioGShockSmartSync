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
        assertEquals("it maps everything (owner 2026-11-24)", Control.ALL, access.controls)
        assertEquals(listOf("your Home Assistant server"), access.sendsTo)
    }

    @Test fun itTellsAboutItself() {
        val p = HomeAssistantPlugin()
        assertTrue(p.summary.length <= 60)
        assertTrue(p.description.isNotBlank() && p.setupNeeds.isNotBlank() && p.author.isNotBlank() && p.license.isNotBlank())
        assertTrue(p.infoUrl.startsWith("https://") && p.sourceUrl.startsWith("https://") && p.issuesUrl.startsWith("https://"))
    }
}
