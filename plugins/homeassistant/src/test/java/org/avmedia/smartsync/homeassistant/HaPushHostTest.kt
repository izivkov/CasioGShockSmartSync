package org.avmedia.smartsync.homeassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HaPushHostTest {
    @Test fun theSocketIsOwnedByTheServiceNotByAScreen() = runTest {
        var started = 0
        val wakes = ArrayList<Boolean>()
        val host = HaPushHost(backgroundScope) {
            started++
            HaPushHost.HostedChannel(run = { awaitCancellation() }, wake = { wakes += it })
        }
        // A screen (Activity) is a scope of its own: it comes and goes, the host does not care.
        val screen = CoroutineScope(Job() + UnconfinedTestDispatcher(testScheduler))
        host.acquire(HaPushHost.SERVICE)
        assertTrue(host.running)
        screen.coroutineContext[Job]!!.cancel()
        assertTrue(host.running)
        host.acquire(HaPushHost.SESSION)
        assertEquals(1, started)
        host.release(HaPushHost.SESSION)
        assertTrue("the session ending must not close the service's socket", host.running)
        host.networkChanged(); host.watchConnected()
        assertEquals(listOf(true, false), wakes)
        host.release(HaPushHost.SERVICE)
        assertFalse(host.running)
    }

    @Test fun aSocketJobThatEndedIsStartedAgainWhenTheWatchConnects() = runTest {
        var started = 0
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> })
        val host = HaPushHost(scope) {
            started++
            HaPushHost.HostedChannel(run = { if (started == 1) error("crashed") else awaitCancellation() }, wake = {})
        }
        host.acquire(HaPushHost.SERVICE)
        testScheduler.advanceUntilIdle()
        assertFalse(host.running)
        host.watchConnected()
        testScheduler.advanceUntilIdle()
        assertEquals(2, started)
        assertTrue(host.running)
    }

    /**
     * The app returns to its main screen after 3 min without input and the inactivity handler
     * disconnects the watch (the session ends). The HA push subscription must stay: same socket, no new subscribe,
     * sensor still on, while the companion service holds the host.
     */
    @Test fun anInactivityDisconnectLeavesTheSubscriptionActive() = runTest {
        var subscribes = 0
        var ends = 0
        val states = ArrayList<Boolean>()
        val host = HaPushHost(backgroundScope) {
            val loop = HaPushLoop(
                connect = { up ->
                    val s = HaPushSession("tok", "hook1",
                        send = { if (it.contains("push_notification_channel")) subscribes++; true },
                        up = up, end = { ends++ }, route = {}, log = {})
                    s.onText("""{"type":"auth_required"}""")
                    s.onText("""{"type":"auth_ok"}""")
                    s.onText("""{"id":1,"type":"result","success":true,"result":null}""")
                    awaitCancellation()
                },
                now = { testScheduler.currentTime },
                log = {},
                onState = { states += it },
            )
            HaPushHost.HostedChannel(run = { loop.run() }, wake = loop::wake)
        }
        host.acquire(HaPushHost.SERVICE)
        host.acquire(HaPushHost.SESSION)
        runCurrent()
        // Inactivity: repository.disconnect() closes the watch session, which releases its hold.
        host.release(HaPushHost.SESSION)
        advanceTimeBy(10 * 60_000L); runCurrent()
        host.acquire(HaPushHost.SESSION); host.watchConnected()
        runCurrent()
        assertTrue(host.running)
        assertEquals("subscribed once, never torn down by the screen or the session", 1, subscribes)
        assertEquals(0, ends)
        assertEquals(listOf(true), states)
    }
}
