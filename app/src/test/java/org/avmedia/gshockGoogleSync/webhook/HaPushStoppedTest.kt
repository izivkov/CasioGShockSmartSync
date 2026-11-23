package org.avmedia.gshockGoogleSync.webhook

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hass saw "push connected on" for hours while the socket was gone: stopping the channel must say off, and a heartbeat must exist. */
@OptIn(ExperimentalCoroutinesApi::class)
class HaPushStoppedTest {
    @After fun reset() { HaPushRouter.updateSensorsHook = null; HaPushState.set(false) }

    @Test fun stoppingTheChannelSetsTheStateOffAndPushesTheSensors() = runTest {
        var pushed = 0
        HaPushRouter.updateSensorsHook = { pushed++ }
        val channel = HaPushChannel(HaStore(HaMemoryStore()), deliver = {}, log = {})
        val job = backgroundScope.launch { channel.run { "id" } }
        testScheduler.advanceTimeBy(100); testScheduler.runCurrent()
        HaPushState.set(true)
        val before = pushed
        job.cancel()
        testScheduler.runCurrent()
        assertFalse(HaPushState.connected)
        assertTrue("the off state must be sent to HA", pushed > before)
    }

    @Test fun theSensorCarriesAHeartbeatAttribute() {
        val s = HaSensors.ALL.first { it.uniqueId == "ha_push_connected" }
        assertEquals("app.ha_push_heartbeat", s.attributes["heartbeat"])
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HaPushWhyNotTest {
    @Test fun aSocketThatCannotStartSaysWhyOnceAndNotPerRetry() = runTest {
        val logs = ArrayList<String>()
        val channel = HaPushChannel(HaStore(HaMemoryStore()), deliver = {}, log = { logs += it })
        backgroundScope.launch { channel.run { "id" } }
        testScheduler.advanceTimeBy(300_000)
        testScheduler.runCurrent()
        assertEquals(listOf("Home Assistant: push loop started", "Home Assistant: push not connected (not set up)"), logs)
    }
}
