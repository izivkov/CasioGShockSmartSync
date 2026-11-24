package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.casio.ConnectionWriter
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.casio.WatchChannel
import org.avmedia.gshockapi.casio.WatchLink
import org.avmedia.gshockapi.casio.WatchQueue
import org.avmedia.gshockapi.casio.WatchTransport
import org.avmedia.gshockapi.casio.WatchWriter
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.protocols.H5600Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A first connection after pairing (kind 10): our setup is done, the watch's `47 01` is late or never comes. A LifeLog read
 * started then replayed its own setup reads next to the connection setup, the workout read went on without `47 01`, the
 * watch dropped the link and the settings reads the screens wait for were held behind the transfer. No transfer starts
 * before `47 01`; without it the transfer is left out for this link.
 */
class TransferWaitsForWatchSetupTest {
    private class Writer : WatchWriter {
        override fun writeAllFeatures(bytes: ByteArray) {}
        override fun request(code: Int) {}
        override fun writeLinkLoss(level: Int) {}
    }

    /** Every frame the transfers put on the link (the connection setup writes through [Writer]). */
    private class Link : WatchTransport {
        val frames = CopyOnWriteArrayList<String>()
        override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
            frames += frame.joinToString("") { "%02x".format(it) }
            return true
        }
    }

    private val link = Link()
    private val workoutWrites = CopyOnWriteArrayList<String>()
    private val savedQueue = WatchLink.queue
    private val savedSink = H5600WorkoutIO.sink
    private val savedWorkoutLink = H5600WorkoutIO.link

    @Before fun install() {
        ConnectionKeepAlive.writer = Writer()
        ConnectionKeepAlive.clearEvents()
        ConnectionSetup.busy = { false }
        ConnectionSetup.timeWriter = {}
        ConnectionSetup.reset()
        H5600HealthIO.stepCounterSupported = { true }
        H5600WorkoutIO.resetBackoff()
        H5600WorkoutIO.sink = { true }
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) { workoutWrites += data.joinToString("") { "%02x".format(it) } }
            override fun bulk(data: ByteArray) { workoutWrites += "bulk " + data.joinToString("") { "%02x".format(it) } }
        }
        WatchLink.queue = WatchQueue(transport = link, readTimeoutMs = 2_000)
    }

    @After fun restore() {
        WatchLink.queue = savedQueue
        H5600WorkoutIO.sink = savedSink
        H5600WorkoutIO.link = savedWorkoutLink
        H5600WorkoutIO.setupWaitMs = ConnectionSetup.SETUP_WAIT_MS
        H5600WorkoutIO.resetBackoff()
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        ConnectionSetup.watchSetupWaitMs = ConnectionSetup.SETUP_WAIT_MS
        ConnectionKeepAlive.writer = ConnectionWriter
        ConnectionSetup.busy = { H5600HealthIO.isActive || H5600WorkoutIO.isActive }
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    private fun receive(data: String) = MessageDispatcher.onReceived(data, H5600Protocol)

    /** The kind 10 setup with the answers of a real first connection: 0x11, 0x3B, 0x3A, the name. No `47 01`. */
    private fun kindTenSetupDone() {
        receive("0x10 EE 02 55 04 76 C5 7F 0A 03")
        receive("0x11 00 00 00 06 00 00 00 00 00")
        receive("0x3B 50 00 0A 40 01 05 05 20 03")
        receive("0x3A 00 34 01 40 01 04 00 58 02")
        receive("0x23 43 41 53 49 4F 20 47 42 58")
        assertTrue(AppLog.recentEvents().toString(), AppLog.recentEvents().any { it.endsWith("time and DST write started") })
    }

    @Test fun aLateSetupDoneStartsNoTransferAndTheSettingsReadsOfTheScreensGoOn() = runBlocking {
        kindTenSetupDone()
        val read = async(Dispatchers.IO) { H5600HealthIO.request(peek = true) }
        delay(500)
        assertTrue("no setup replay, no START before 47 01: ${link.frames}", link.frames.isEmpty())
        assertTrue("no transfer holds the link", !WatchExchange.transferRunning())
        assertNotNull("a settings read does not wait", withTimeoutOrNull(300) { WatchExchange.awaitNoTransfer() })

        ConnectionSetup.onWatchSetupDone()
        val first = withTimeoutOrNull(3_000) { while (link.frames.isEmpty()) delay(20); link.frames.first() }
        read.cancel()
        assertEquals("after 47 01 the read starts with the step goal, no setup replay", "43", first)
    }

    @Test fun withoutSetupDoneTheTransfersAreLeftOutAndNothingIsSent() = runBlocking {
        kindTenSetupDone()
        ConnectionSetup.watchSetupWaitMs = 500
        H5600WorkoutIO.setupWaitMs = 500

        val data = withTimeoutOrNull(5_000) { H5600HealthIO.request(peek = true) }
        val workouts = withTimeoutOrNull(5_000) { H5600WorkoutIO.download() }

        assertNotNull("the LifeLog read ends", data)
        assertNull(data!!.h5600)
        assertNull("no workout read", workouts)
        assertTrue("nothing on the link: ${link.frames}", link.frames.isEmpty())
        assertTrue("no workout session: $workoutWrites", workoutWrites.isEmpty())
        val lines = AppLog.recentEvents()
        assertTrue(lines.toString(), lines.none { it.contains("going on") })
    }
}
