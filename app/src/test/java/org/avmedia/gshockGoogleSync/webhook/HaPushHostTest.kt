package org.avmedia.gshockGoogleSync.webhook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        val host = HaPushHost(backgroundScope) {
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
}
