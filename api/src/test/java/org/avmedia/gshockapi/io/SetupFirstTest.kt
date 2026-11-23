package org.avmedia.gshockapi.io

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.casio.ConnectionWriter
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.casio.WatchWriter
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Log 2026-11-23 15:44:24: the connection setup (kind 14) started, a settings or LifeLog sequence started at once
 * (0x23, 0x10, 0x11, 0x3B ...), and the setup got none of its answers (no "0x23 name written back", no 47 01).
 * The setup runs first; the rest waits for it.
 */
class SetupFirstTest {
    private class Writer : WatchWriter {
        val all = ArrayList<List<Int>>()
        val requests = ArrayList<Int>()
        override fun writeAllFeatures(bytes: ByteArray) { all += bytes.map { it.toInt() and 0xFF } }
        override fun request(code: Int) { requests += code }
        override fun writeLinkLoss(level: Int) {}
    }

    private val writer = Writer()
    private val link = FakeSettingLink()

    @Before fun install() {
        ConnectionKeepAlive.writer = writer
        ConnectionKeepAlive.clearEvents()
        ConnectionSetup.busy = { false }
        ConnectionSetup.timeWriter = {}
        RawSettingIO.link = link
        RawSettingIO.settleMs = 0
        ConnectionSetup.reset()
    }

    @After fun restore() {
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
        ConnectionKeepAlive.writer = ConnectionWriter
        ConnectionSetup.busy = { H5600HealthIO.isActive || H5600WorkoutIO.isActive }
        RawSettingIO.link = AndroidSettingLink
        RawSettingIO.settleMs = 1_000L
    }

    private fun receive(data: String) = MessageDispatcher.onReceived(data, H5600Protocol)

    private fun startKind14() = receive("0x10 00 00 00 00 00 00 00 0E 00")

    @Test fun aSettingsReadWaitsUntilTheSetupHasItsAnswers() = runBlocking {
        startKind14()
        assertTrue(ConnectionSetup.isRunning())
        val read = async { RawSettingIO.read(byteArrayOf(0x45), 2_000) }
        delay(300)
        assertTrue("nothing may be sent in front of the setup's requests", link.requests.isEmpty())
        link.onRequest = { RawSettingIO.offer("0x45 01 02 03") }
        receive("0x23 41 42 43")
        receive("0x39 04")
        assertFalse(ConnectionSetup.isRunning())
        assertEquals(listOf(0x45, 1, 2, 3), read.await().frame!!.map { it.toInt() and 0xFF })
    }

    @Test fun aSettingsReadOfTheNameDoesNotSwallowTheSetupAnswer() = runBlocking {
        // A read of 0x23 is already on the wire when the kind answer starts the setup (both wait for the same code).
        val read = async { RawSettingIO.read(byteArrayOf(0x23), 2_000) }
        while (link.requests.isEmpty()) delay(10)
        startKind14()
        receive("0x23 41 42 43")
        assertEquals(0x23, read.await().frame!![0].toInt())
        assertTrue(AppLog.recentEvents().toString(), AppLog.recentEvents().any { it.endsWith("0x23 name written back") })
    }

    @Test fun theWaitEndsAtTheTimeoutAndSaysSo() = runBlocking {
        startKind14()
        assertFalse(ConnectionSetup.awaitIdle(timeoutMs = 150, pollMs = 10))
        assertTrue(AppLog.recentEvents().any { it.contains("still at") })
    }

    @Test fun aKindAnswerAlwaysWritesALogLine() {
        receive("0x10 00 00 00 00 00 00 00 0E 00")
        receive("0x10 00 00 00 00 00 00 00 0E 00")
        val lines = AppLog.recentEvents()
        assertTrue(lines.toString(), lines.any { it.endsWith("Connection kind 14: setup starts") })
        assertTrue(lines.toString(), lines.any { it.contains("setup skipped") })
    }
}
