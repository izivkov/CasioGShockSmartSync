package org.avmedia.gshockapi.io

import CachedIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Owner log 2026-11-23_2309 lines 3570-3586: a settings read (`0x11`) went out every 4 to 5 s while a workout read held the link. */
class SettingsReadTransferGateTest {
    private val saved = WatchExchange.transferRunning

    @After fun restore() {
        WatchExchange.transferRunning = saved
        CachedIO.remove("GATE_TEST")
    }

    @Test fun aSettingsReadDoesNotGoOutWhileATransferRunsAndGoesOutAfterIt() = runBlocking {
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        WatchExchange.transferRunning = { running.get() }
        val sent = java.util.concurrent.atomic.AtomicInteger(0)
        val read = async(Dispatchers.Default) { CachedIO.request("GATE_TEST") { sent.incrementAndGet(); 7 } }
        delay(400)
        assertEquals("sent during the transfer", 0, sent.get())
        running.set(false)
        assertEquals(7, withTimeout(3_000) { read.await() })
        assertEquals(1, sent.get())
    }
}
