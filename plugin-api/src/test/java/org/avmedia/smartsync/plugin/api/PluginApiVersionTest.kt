package org.avmedia.smartsync.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluginApiVersionTest {
    /** The published version (build file, tag plugin-api-v<version>) is the one the code states. */
    @Test fun theBuildFileAndTheConstantNameTheSameVersion() {
        val build = File("build.gradle").readText()
        val declared = Regex("""(?m)^version = '([^']+)'""").find(build)?.groupValues?.get(1)
        assertEquals(PluginApi.VERSION, declared)
        assertTrue("CHANGELOG.md names ${PluginApi.VERSION}", File("CHANGELOG.md").readText().contains("## ${PluginApi.VERSION}"))
    }

    /** Until 1.0 a patch only adds (README): a host runs its own minor up to its own patch; a new minor may break. */
    @Test fun zeroMajorNeedsTheSameMinorAndNoNewerPatch() {
        assertTrue(PluginApi.compatible("0.1.0", "0.1.3"))
        assertTrue(PluginApi.compatible("0.1.3", "0.1.3"))
        assertTrue(PluginApi.compatible("0.1", "0.1.0"))
        assertFalse("a newer patch may call what this host lacks", PluginApi.compatible("0.1.2", "0.1.0"))
        assertFalse(PluginApi.compatible("0.2.0", "0.1.0"))
        assertFalse(PluginApi.compatible("0.1.0", "0.2.0"))
    }

    @Test fun atLeastComparesAllThreeParts() {
        assertTrue(PluginApi.atLeast("0.1.0", "0.1.0"))
        assertTrue(PluginApi.atLeast("0.1.2", "0.1.1"))
        assertTrue(PluginApi.atLeast("1.0.0", "0.9.9"))
        assertFalse(PluginApi.atLeast("0.1.0", "0.1.1"))
        assertFalse(PluginApi.atLeast("bad", "0.1.0"))
    }

    @Test fun fromOneTheHostRunsOlderMinorsOfItsMajor() {
        assertTrue(PluginApi.compatible("1.0.0", "1.3.0"))
        assertFalse(PluginApi.compatible("1.4.0", "1.3.0"))
        assertFalse(PluginApi.compatible("2.0.0", "1.9.0"))
        assertFalse(PluginApi.compatible("1.0.0", "2.0.0"))
    }

    @Test fun aBrokenVersionIsNeverCompatible() {
        assertFalse(PluginApi.compatible("", "0.1.0"))
        assertFalse(PluginApi.compatible("x.1", "0.1.0"))
        assertFalse(PluginApi.compatible("0.1.0.4", "0.1.0"))
        assertFalse(PluginApi.compatible("-1.1", "0.1.0"))
    }
}
