package org.avmedia.gshockapi.io

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.log.AppLog
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Logs 2026-11-23b/e: every background and Health screen read is a peek read, and the merged peek test did not echo
 * END, so the watch dropped the link about 16 s after the data (e, 16:11:28 END, 16:11:44 new link). A read that was
 * still running when the link dropped ran its setup on the next link and took the init's 0x10 answer
 * (b, 15:42:33 to 15:42:35: "Timed out waiting for button info").
 */
class LifeLogLinkTest {
    private val end = byteArrayOf(0x04, 0x2E, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

    private fun endWrites(sync: H5600LifeLogSync): List<H5600Write> {
        sync.start()
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(0, 0, 0))
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(4, 0))
        sync.onMessage(H5600Channel.CONVOY, byteArrayOf(4, 0))
        sync.onMessage(H5600Channel.CONVOY, ByteArray(9) { 0x06.toByte() })
        sync.onMessage(H5600Channel.CONVOY, ByteArray(9) { 0x06.toByte() })
        return sync.onMessage(H5600Channel.DRSP, end)
    }

    @Before fun install() {
        H5600HealthIO.stepCounterSupported = { true }
        ConnectionSetup.reset()
    }

    @After fun restore() {
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    @Test fun aPeekReadEchoesEndLikeEveryRead() {
        for (peek in listOf(true, false)) {
            val writes = endWrites(H5600HealthIO.syncFor(peek))
            assertTrue("peek=$peek: END must be echoed, else the watch drops the link", writes.any { it.data.contentEquals(end) })
        }
    }

    @Test fun aReadEndsWhenTheLinkDropsAndTriesNoMore() = runBlocking {
        val read = async { H5600HealthIO.request(peek = true) }
        while (!H5600HealthIO.isActive) delay(10)
        H5600HealthIO.onLinkLost()
        val data = withTimeout(3_000) { read.await() }
        assertNull(data.h5600)
        assertFalse(H5600HealthIO.isActive)
        val lines = AppLog.recentEvents()
        assertTrue(lines.toString(), lines.none { it.contains("LifeLog attempt 1 of") })
        assertTrue(lines.toString(), lines.any { it.contains("LifeLog read stopped: the link dropped") })
    }
}
