package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockapi.WatchInfo
import org.avmedia.smartsync.homeassistant.HaSensors
import org.avmedia.smartsync.homeassistant.WatchCap
import org.avmedia.smartsync.homeassistant.WatchCaps
import org.avmedia.smartsync.plugin.api.Capability
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says a model can do, as the Home Assistant plugin reads it: the OCW-S7000 gets no health, workout or
 * settings sensor, the GBX keeps every sensor (so its registration and entity ids stay as they were).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HaCapsContractTest {
    @org.junit.Before fun main() = kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())

    @After fun reset() {
        WatchInfo.reset()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test fun theOcwS7000HasBatteryAndTemperatureButNoHealth() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        val caps = Capabilities.connected(notifications = false)
        assertFalse(Capability.HEALTH_LOG in caps)
        val ha = WatchCaps.of(caps)
        assertFalse(WatchCap.H5600 in ha)
        val ids = HaSensors.forCaps(HaSensors.ALL, ha).map { it.variable }
        assertTrue(ids.toString(), ids.none { it.startsWith("health.") || it.startsWith("workout.") || it.startsWith("watch.setting") })
        assertTrue("watch.battery" in ids && "watch.temperature" in ids)
    }

    @Test fun theGbxHasEverythingHomeAssistantHad() {
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertEquals(WatchCaps.ALL, WatchCaps.of(Capabilities.connected(notifications = true)))
    }
}
