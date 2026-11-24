package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
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
import org.avmedia.gshockapi.log.LogSource
import org.avmedia.gshockapi.model.WorkoutSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun bytes(hex: String): ByteArray = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

/** `0x28` with index 6 = 1: a workout is waiting. */
private val WAITING = bytes("28 06 17 00 00 FA 00 01 04 00")

/**
 * A watch that answers the quick setup (`0x28` = [WAITING]) and plays a recorded LifeLog transfer (resource
 * `h5600_lifelog_2026-10-28.txt`) after START: index announcements, the bulk frames, then END.
 */
private class LifeLogWatch : WatchTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var sixSeen = 0
    private val announced = ArrayList<Pair<Int, Int>>()
    private val frames = ArrayList<String>()

    init {
        for (line in javaClass.getResourceAsStream("/h5600_lifelog_2026-10-28.txt")!!.bufferedReader().readLines()) {
            val part = line.trim().split(" ")
            when (part[0]) {
                "A" -> announced += part[1].toInt(16) to part[2].toInt()
                "N" -> frames += part[1].chunked(2).joinToString(" ")
            }
        }
    }

    private fun hex(vararg b: Int) = b.joinToString(" ") { "%02X".format(it) }

    private fun status(bytes: ByteArray) {
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        WatchLink.onNotification(CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID, hex)
        if (H5600HealthIO.isAwaitingSetupReply()) H5600HealthIO.onStatus(hex)
    }

    private fun later(block: suspend () -> Unit) {
        scope.launch {
            delay(10)
            block()
        }
    }

    override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
        val f = frame.map { it.toInt() and 0xFF }
        when (channel) {
            WatchChannel.READ_REQUEST -> later { status(if (f[0] == 0x28) WAITING else ByteArray(20).also { it[0] = f[0].toByte(); it[1] = 0x40; it[2] = 0x1F }) }
            WatchChannel.DATA_REQUEST ->
                if (f[0] == 0x00 && f[1] == 0x2E && f[2] == 0xFF && sixSeen >= 2) later {
                    for ((index, length) in announced) H5600HealthIO.onDrsp(hex(0x00, 0x2E, index, length and 0xFF, (length shr 8) and 0xFF, (length shr 16) and 0xFF))
                    for (n in frames) H5600HealthIO.onConvoy(n)
                    H5600HealthIO.onDrsp(hex(0x04, 0x2E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF))
                }
            WatchChannel.CONVOY -> when {
                f.size == 3 && f.all { it == 0 } -> later { H5600HealthIO.onConvoy(hex(0, 0, 0)) }
                f[0] == 0x04 -> later { H5600HealthIO.onConvoy(hex(0x04, 0x00)) }
                f[0] == 0x06 -> { sixSeen++; later { H5600HealthIO.onConvoy(hex(0x06, 0xFA, 0, 2, 0, 0, 0, 0, 0)) } }
            }
            else -> Unit
        }
        return true
    }
}

/**
 * Every Health read also reads the workouts the watch holds: they are stored once and the LifeLog result stays, also when
 * the workout transfer fails. A transfer problem is one App log line with the source Workouts.
 */
class HealthReadWorkoutsTest {
    private val savedQueue = WatchLink.queue
    private val savedSink = H5600WorkoutIO.sink
    private val savedLink = H5600WorkoutIO.link
    private val forward = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before fun install() {
        H5600HealthIO.stepCounterSupported = { true }
        H5600HealthIO.readyWaitMs = 5_000L
        ConnectionSetup.busy = { false }
        ConnectionSetup.reset()
        ConnectionSetup.onWatchSetupDone()
        ConnectionKeepAlive.clearEvents()
        H5600WorkoutIO.resetBackoff()
        WatchLink.queue = WatchQueue(transport = LifeLogWatch(), readTimeoutMs = 2_000)
    }

    @After fun restore() {
        forward.cancel()
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        H5600HealthIO.readyWaitMs = 60_000L
        H5600WorkoutIO.sink = savedSink
        H5600WorkoutIO.link = savedLink
        H5600WorkoutIO.resetBackoff()
        WatchLink.queue = savedQueue
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    /** The scripted workout watch of [FakeWatch], its frames delivered the way the connection delivers them. */
    private fun workoutWatch(): FakeWatch {
        val toApp = Channel<WorkoutMsg>(Channel.UNLIMITED)
        forward.launch {
            for (m in toApp) {
                val hex = m.data.joinToString(" ") { "%02X".format(it) }
                if (m.channel == H5600Channel.DRSP) H5600WorkoutIO.onDrsp(hex) else H5600WorkoutIO.onConvoy(hex)
            }
        }
        return FakeWatch(toApp, keepsMarks = true).also { H5600WorkoutIO.link = it }
    }

    private fun linesAfter(marker: String): List<String> {
        val all = AppLog.recentEvents()
        return all.drop(all.indexOfLast { it.contains(marker) } + 1)
    }

    @Test fun aHealthReadStoresTheWaitingWorkoutOnceAndASecondReadAddsNothing() = runBlocking {
        val stored = CopyOnWriteArrayList<WorkoutSession>()
        H5600WorkoutIO.sink = { stored += it; true }
        val watch = workoutWatch()

        val first = H5600HealthIO.request(peek = true)
        delay(1_000) // a read started after the LifeLog would run now
        val readsAfterFirst = watch.events.count { it == "read 29 0" }
        val second = H5600HealthIO.request(peek = true)
        delay(1_000)

        val why = "events ${watch.events}"
        assertNotNull("LifeLog result of the first read", first.h5600)
        assertNotNull("LifeLog result of the second read", second.h5600)
        assertEquals("one workout stored: $why", 1, stored.size)
        assertEquals("marked once: $why", 1, watch.marks.size)
        assertEquals("one workout read per Health read: $why", 1, readsAfterFirst)
    }

    @Test fun aFailedWorkoutTransferKeepsTheLifeLogResultAndWritesOneAppLogLine() = runBlocking {
        H5600WorkoutIO.sink = { true }
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) {}

            override fun bulk(data: ByteArray) {
                if (data.size == 3 && data.all { it.toInt() == 0 }) H5600WorkoutIO.onDrsp("03 01")
            }
        }
        val marker = "test marker ${System.nanoTime()}"
        AppLog.log(LogSource.App, marker)

        val data = H5600HealthIO.request(peek = true)
        delay(1_000)

        val lines = linesAfter(marker)
        assertNotNull("LifeLog result: $lines", data.h5600)
        val workouts = lines.filter { it.contains("[Workouts]") }
        assertEquals("one Workouts line: $lines", 1, workouts.size)
        assertTrue(workouts.single(), workouts.single().contains("fail reason 1"))
        assertTrue("the source exists for the App log filter", LogSource.ofLabel("Workouts") != null)
    }
}
