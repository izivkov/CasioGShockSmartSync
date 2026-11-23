package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The owner's `0x28` answers of log 2026-11-23h: 21:25:45 (no workout waiting) and 21:31:39 (index 6 = 1, workout waiting). */
private fun frame(hex: String): ByteArray = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
private val NO_WORKOUT = frame("28 06 18 00 00 FA 00 00 04 00")
private val WORKOUT_WAITING = frame("28 06 17 00 00 FA 00 01 04 00")
private val WORKOUT_WAITING_LATER = frame("28 06 17 00 00 FA 00 01 00 00")

/**
 * A watch that answers `0x28` with [condition] and, while it holds an unfetched workout, never answers the LifeLog START or
 * its polls (log 2026-11-23h 21:31:39 to 21:32:40). Once the workouts are fetched it behaves as on every earlier read.
 */
private class WorkoutHoldingWatch(private val condition: ByteArray) : WatchTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val events = CopyOnWriteArrayList<String>()
    @Volatile var workoutsPending = true
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
        if (H5600HealthIO.isAwaitingSetupReply()) H5600HealthIO.onStatus(hex)
    }

    private fun convoy(vararg b: Int) = H5600HealthIO.onConvoy(b.joinToString(" ") { "%02X".format(it) })

    private fun drsp(vararg b: Int) = H5600HealthIO.onDrsp(b.joinToString(" ") { "%02X".format(it) })

    override suspend fun write(channel: WatchChannel, frame: ByteArray): Boolean {
        delay(EVENT_MS)
        val f = frame.map { it.toInt() and 0xFF }
        when (channel) {
            WatchChannel.READ_REQUEST -> later {
                status(if (f[0] == 0x28) condition else ByteArray(20).also { it[0] = f[0].toByte(); it[1] = 0x40; it[2] = 0x1F })
            }
            WatchChannel.DATA_REQUEST ->
                if (f[0] == 0x00 && f[1] == 0x2E && f[2] == 0xFF) {
                    events += if (workoutsPending) "START (workout pending)" else "START"
                    if (!workoutsPending && sixSeen >= 2) later { drsp(0x04, 0x2E, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF) }
                }
            WatchChannel.CONVOY -> when {
                f.size == 3 && f.all { it == 0 } -> if (!workoutsPending) later { convoy(0, 0, 0) }
                f[0] == 0x04 -> later { convoy(0x04, 0x00) }
                f[0] == 0x06 -> { sixSeen++; later { convoy(0x06, 0xFA, 0, 2, 0, 0, 0, 0, 0) } }
            }
            else -> Unit
        }
        return true
    }

    companion object {
        const val EVENT_MS = 20L
    }
}

/** Workouts flagged in `0x28` are read before the LifeLog START (notes T4), so the read no longer hangs on such a watch. */
class LifeLogWorkoutsFirstTest {
    private val savedQueue = WatchLink.queue
    private val savedRun = H5600HealthIO.workoutsFirst

    @Before fun install() {
        H5600HealthIO.stepCounterSupported = { true }
        H5600HealthIO.readyWaitMs = 1_500L
        ConnectionSetup.busy = { false }
        ConnectionSetup.reset()
        ConnectionSetup.onWatchSetupDone()
        ConnectionKeepAlive.clearEvents()
    }

    @After fun restore() {
        H5600HealthIO.stepCounterSupported = { org.avmedia.gshockapi.WatchInfo.hasStepCounter }
        H5600HealthIO.readyWaitMs = 60_000L
        H5600HealthIO.workoutsFirst = savedRun
        WatchLink.queue = savedQueue
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    private fun run(condition: ByteArray): Pair<WorkoutHoldingWatch, List<String>> = runBlocking {
        val watch = WorkoutHoldingWatch(condition)
        WatchLink.queue = WatchQueue(transport = watch, readTimeoutMs = 2_000)
        H5600HealthIO.workoutsFirst = {
            watch.events += "workouts read"
            watch.workoutsPending = false
        }
        H5600HealthIO.request(peek = true)
        watch to AppLog.recentEvents()
    }

    @Test fun theFlagIsReadFromTheOwnersFrames() {
        assertEquals(0, WorkoutReadTrigger.flagOf(NO_WORKOUT))
        assertEquals(1, WorkoutReadTrigger.flagOf(WORKOUT_WAITING))
        assertEquals(1, WorkoutReadTrigger.flagOf(WORKOUT_WAITING_LATER))
        assertNull(WorkoutReadTrigger.flagOf(frame("28 06 17")))
    }

    @Test fun aFlaggedWorkoutIsReadBeforeTheLifeLogStartAndTheReadNeedsOneAttempt() {
        val (watch, lines) = run(WORKOUT_WAITING)
        assertEquals("workouts read", watch.events.first())
        assertTrue(watch.events.toString(), watch.events.none { it == "START (workout pending)" })
        assertTrue(lines.toString(), lines.none { it.contains("LifeLog attempt 1 of") })
        assertTrue(lines.toString(), lines.any { it.contains("0x28 index 6 is 1, the waiting workouts are read first") })
    }

    @Test fun withoutTheFlagNoWorkoutReadRunsBeforeTheLifeLog() {
        val (watch, _) = run(NO_WORKOUT)
        assertTrue(watch.events.toString(), watch.events.none { it == "workouts read" })
    }

    @Test fun aFailingWorkoutReadIsLoggedAndTheLifeLogGoesOn() = runBlocking {
        val watch = WorkoutHoldingWatch(WORKOUT_WAITING)
        WatchLink.queue = WatchQueue(transport = watch, readTimeoutMs = 2_000)
        H5600HealthIO.workoutsFirst = {
            watch.workoutsPending = false
            error("no answer")
        }
        H5600HealthIO.request(peek = true)
        val lines = AppLog.recentEvents()
        assertTrue(lines.toString(), lines.any { it.contains("Workout read before the LifeLog failed") })
        assertTrue(lines.toString(), lines.any { it.contains("LifeLog read goes on after the workouts") })
        assertTrue(watch.events.toString(), watch.events.contains("START"))
    }

    /** The button while a LifeLog read holds the link: before, it waited without a line; now every step is in the log. */
    @Test fun aWorkoutReadLogsTheWaitItsStepsAndWhyItStopped() = runBlocking {
        val savedSink = H5600WorkoutIO.sink
        val savedLink = H5600WorkoutIO.link
        try {
            H5600WorkoutIO.sink = { true }
            H5600WorkoutIO.link = object : WorkoutLink {
                override fun drsp(data: ByteArray) {}
                override fun bulk(data: ByteArray) {}
            }
            WatchExchange.mutex.lock()
            val read = async(Dispatchers.IO) { H5600WorkoutIO.download() }
            delay(300)
            WatchExchange.mutex.unlock()
            val result = read.await()
            val lines = AppLog.recentEvents().filter { it.contains("Workout read") }
            assertTrue(lines.toString(), result?.error.orEmpty().contains("did not answer the start"))
            for (want in listOf("Workout read asked", "Workout read waits", "Workout read starts", "session 1C requested", "Workout read stopped: the watch did not answer the start")) {
                assertTrue("$want in $lines", lines.any { it.contains(want) })
            }
        } finally {
            H5600WorkoutIO.sink = savedSink
            H5600WorkoutIO.link = savedLink
        }
    }
}
