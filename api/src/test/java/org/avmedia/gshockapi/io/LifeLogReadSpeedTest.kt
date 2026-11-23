package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.casio.WatchChannel
import org.avmedia.gshockapi.casio.WatchLink
import org.avmedia.gshockapi.casio.WatchQueue
import org.avmedia.gshockapi.casio.WatchTransport
import org.avmedia.gshockapi.log.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A watch on the slow always-connected link, with the timings of log 2026-11-23e/f: every write takes one connection event
 * (0.39 s, two with response), every answer comes one event later (a read costs 0.78 s, a read plus echo 1.56 s). After START
 * it answers three polls busy (`00 01`), is silent while it prepares and answers ready 10 s after the first START (log e
 * 14:38:59 to 14:39:10). No new data: the transfer is the parameter exchange and END.
 */
private class SlowLinkWatch(private val prepMs: Long = 10_000L) : WatchTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reads = CopyOnWriteArrayList<Int>()
    @Volatile private var firstStart = 0L
    @Volatile private var busyAnswers = 0
    @Volatile private var sixSeen = 0

    private fun later(block: () -> Unit) {
        scope.launch {
            delay(EVENT_MS)
            block()
        }
    }

    private fun status(bytes: ByteArray) {
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        WatchLink.onNotification(CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID, hex)
        // H5600Protocol hands STATUS to the read only while a setup reply is awaited.
        if (H5600HealthIO.isAwaitingSetupReply()) H5600HealthIO.onStatus(hex)
    }

    private fun convoy(vararg b: Int) = H5600HealthIO.onConvoy(b.joinToString(" ") { "%02X".format(it) })

    private fun drsp(vararg b: Int) = H5600HealthIO.onDrsp(b.joinToString(" ") { "%02X".format(it) })

    override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
        delay(if (channel.withResponse) 2 * EVENT_MS else EVENT_MS)
        val f = frame.map { it.toInt() and 0xFF }
        when (channel) {
            WatchChannel.READ_REQUEST -> {
                reads += f[0]
                later { status(ByteArray(20).also { it[0] = f[0].toByte(); it[1] = 0x40; it[2] = 0x1F }) }
            }
            WatchChannel.DATA_REQUEST ->
                if (f[0] == 0x00 && f[1] == 0x2E && f[2] == 0xFF) {
                    if (sixSeen >= 2) later { drsp(0x04, 0x2E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF) }
                    else if (firstStart == 0L) firstStart = System.currentTimeMillis()
                }
            WatchChannel.CONVOY -> when {
                f.size == 3 && f.all { it == 0 } -> {
                    val ready = firstStart != 0L && System.currentTimeMillis() - firstStart >= prepMs
                    when {
                        ready -> later { convoy(0, 0, 0) }
                        busyAnswers < 3 -> { busyAnswers++; later { convoy(0, 1, 0) } }
                        else -> Unit // preparing: silent
                    }
                }
                f[0] == 0x04 -> later { convoy(0x04, 0x00) }
                f[0] == 0x06 -> { sixSeen++; later { convoy(0x06, 0xFA, 0, 2, 0, 0, 0, 0, 0) } }
            }
            else -> Unit
        }
        return true
    }

    companion object {
        const val EVENT_MS = 390L
    }
}

/** LifeLog read speed (owner 2026-11-23: "soooo slow", 56 and 61 s for a read with no new data, log 2026-11-23f). */
class LifeLogReadSpeedTest {
    private val savedQueue = WatchLink.queue

    @Before fun install() {
        H5600HealthIO.stepCounterSupported = { true }
        ConnectionSetup.busy = { false }
        ConnectionSetup.reset()
        ConnectionKeepAlive.clearEvents()
    }

    @After fun restore() {
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        H5600HealthIO.readyWaitMs = 60_000L
        WatchLink.queue = savedQueue
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    private fun install(watch: SlowLinkWatch) {
        WatchLink.queue = WatchQueue(transport = watch, readTimeoutMs = 2_000)
    }

    @Test fun aReadWithNoNewDataOnASetUpLinkTakesOneAttemptAndNoSetupReplay() = runBlocking {
        val watch = SlowLinkWatch()
        install(watch)
        ConnectionSetup.onWatchSetupDone()
        val started = System.currentTimeMillis()
        H5600HealthIO.request(peek = true)
        val seconds = (System.currentTimeMillis() - started) / 1000.0
        val lines = AppLog.recentEvents()
        println("LifeLog read on the slow link: $seconds s, reads ${watch.reads.map { "%02X".format(it) }}")
        assertTrue(lines.toString(), lines.none { it.contains("LifeLog attempt 1 of") })
        assertEquals("only the step goal and the watch condition are read", listOf(0x43, 0x28), watch.reads.toList())
        // Old code: 12 reads and 6 echoes per attempt (about 14 s), the first attempt given up before the watch was ready.
        assertTrue("read took $seconds s", seconds < 22.0)
    }

    @Test fun withoutTheWatchsSetupDoneTheFullSetupIsReplayed() = runBlocking {
        val watch = SlowLinkWatch(prepMs = 0)
        install(watch)
        H5600HealthIO.request(peek = true)
        assertEquals(H5600LifeLogSync.SETUP_COMMANDS, watch.reads.toList())
    }

    @Test fun aWatchThatNeverGetsReadyStillEndsTheRead() = runBlocking {
        H5600HealthIO.readyWaitMs = 1_500L
        val watch = SlowLinkWatch(prepMs = Long.MAX_VALUE)
        install(watch)
        ConnectionSetup.onWatchSetupDone()
        val started = System.currentTimeMillis()
        val data = H5600HealthIO.request(peek = true)
        assertEquals(null, data.h5600)
        assertTrue(H5600HealthIO.lastError.orEmpty(), H5600HealthIO.lastError.orEmpty().contains("could not be read"))
        assertTrue(System.currentTimeMillis() - started < 30_000)
    }
}
