package org.avmedia.gshockGoogleSync.homeassistant

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaPushLoopTest {
    private class Rig(scope: TestScope, val script: suspend Rig.(call: Int, up: () -> Unit) -> String) {
        val times = ArrayList<Long>()
        val logs = ArrayList<String>()
        val ups = ArrayList<Pair<Long?, Long>>()
        val states = ArrayList<Boolean>()
        val loop = HaPushLoop(
            connect = { up -> times += scope.currentTime; script(this, times.size, up) },
            now = { scope.currentTime },
            log = { logs += it },
            onState = { states += it },
            onUp = { since, at -> ups += since to at },
        )
    }

    @Test fun aDroppedSocketReconnectsAtOnceThenWithTheBackoff() = runTest {
        val rig = Rig(this) { call, up ->
            when (call) {
                1 -> { up(); "closed 1006" }
                in 2..8 -> "refused"
                else -> { up(); awaitCancellation() }
            }
        }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(200_000); runCurrent()
        assertEquals(listOf(0L, 0L, 1_000L, 3_000L, 8_000L, 18_000L, 48_000L, 108_000L, 168_000L), rig.times)
    }

    @Test fun oneLostLineAndOneBackLinePerOutage() = runTest {
        val rig = Rig(this) { call, up ->
            when (call) {
                1 -> { up(); "closed 1006" }
                in 2..8 -> "refused"
                else -> { up(); awaitCancellation() }
            }
        }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(200_000); runCurrent()
        assertEquals(listOf("Home Assistant: push connection lost (closed 1006)", "Home Assistant: push connection back after 168 s"), rig.logs)
        assertEquals(listOf(true, false, true), rig.states)
        assertEquals(listOf<Pair<Long?, Long>>(null to 0L, 0L to 168_000L), rig.ups)
    }

    @Test fun neverConnectedIsNotAnOutageAndLogsNothing() = runTest {
        val rig = Rig(this) { _, _ -> "not set up" }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(300_000); runCurrent()
        assertTrue(rig.times.size > 3)
        assertTrue(rig.logs.isEmpty())
    }

    @Test fun aNetworkChangeEndsTheLiveSocketAndReconnectsAtOnce() = runTest {
        val rig = Rig(this) { call, up -> if (call == 1) { up(); awaitCancellation() } else { up(); awaitCancellation() } }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(5_000); runCurrent()
        rig.loop.wake(true)
        runCurrent()
        assertEquals(listOf(0L, 5_000L), rig.times)
        assertEquals(listOf("Home Assistant: push connection lost (network changed)", "Home Assistant: push connection back after 0 s"), rig.logs)
    }

    @Test fun aNetworkChangeCutsTheWaitAndResetsTheBackoff() = runTest {
        val rig = Rig(this) { _, _ -> "refused" }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(2_500); runCurrent() // tries at 0, 1000 and then waits 2000 s until 3000
        assertEquals(listOf(0L, 1_000L), rig.times)
        rig.loop.wake(true)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 2_500L), rig.times)
        advanceTimeBy(1_100); runCurrent() // backoff restarted: next wait is 1 s
        assertEquals(listOf(0L, 1_000L, 2_500L, 3_500L), rig.times)
    }

    @Test fun theWatchConnectingCutsTheWaitButKeepsALiveSocket() = runTest {
        val rig = Rig(this) { call, up -> if (call == 1) { up(); awaitCancellation() } else "refused" }
        backgroundScope.launch { rig.loop.run() }
        advanceTimeBy(1_000); runCurrent()
        rig.loop.wake(false)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(0L), rig.times)
        assertTrue(rig.logs.isEmpty())
    }

    @Test fun aFailingStateCallbackDoesNotEndTheLoop() = runTest {
        val times = ArrayList<Long>()
        val loop = HaPushLoop(
            connect = { up -> times += currentTime; if (times.size == 1) { up(); "closed 1006" } else { up(); awaitCancellation() } },
            now = { currentTime },
            log = {},
            onState = { if (!it) error("hook failed") },
        )
        backgroundScope.launch { loop.run() }
        advanceTimeBy(10_000); runCurrent()
        assertEquals("the loop must connect again after the failing callback", 2, times.size)
    }
}
