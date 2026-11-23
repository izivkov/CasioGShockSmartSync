package org.avmedia.gshockapi.io

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockapi.casio.ConnectionSetup
import org.avmedia.gshockapi.log.AppLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Before: a workout read started before `47 01`, and reads from the after-read path and the
 * button queued behind a running one and started again right after it failed (six tries in 14 minutes).
 */
class H5600WorkoutReadGateTest {
    private val savedSink = H5600WorkoutIO.sink
    private val savedLink = H5600WorkoutIO.link
    private var now = 1_000_000L

    /** Answers the start with the fail reason `03 01` so each read fails at once. */
    private val writes = CopyOnWriteArrayList<String>()

    @Before fun install() {
        ConnectionSetup.reset()
        H5600WorkoutIO.resetBackoff()
        H5600WorkoutIO.clock = { now }
        H5600WorkoutIO.sink = { true }
        H5600WorkoutIO.link = object : WorkoutLink {
            override fun drsp(data: ByteArray) {
                writes += data.joinToString("") { "%02x".format(it) }
            }

            override fun bulk(data: ByteArray) {
                writes += "bulk " + data.joinToString("") { "%02x".format(it) }
                if (data.size == 3 && data.all { it.toInt() == 0 }) H5600WorkoutIO.onDrsp("03 01")
            }
        }
    }

    @After fun restore() {
        H5600WorkoutIO.sink = savedSink
        H5600WorkoutIO.link = savedLink
        H5600WorkoutIO.clock = System::currentTimeMillis
        H5600WorkoutIO.setupWaitMs = ConnectionSetup.SETUP_WAIT_MS
        H5600WorkoutIO.resetBackoff()
        ConnectionSetup.reset()
        if (WatchExchange.mutex.isLocked) WatchExchange.mutex.unlock()
    }

    @Test fun noWorkoutRequestBeforeTheWatchSentItsSetupDone() = runBlocking {
        H5600WorkoutIO.setupWaitMs = 10_000L
        val read = async(Dispatchers.IO) { H5600WorkoutIO.download() }
        delay(400)
        assertTrue(writes.toString(), writes.isEmpty())
        assertTrue(AppLog.recentEvents().any { it.contains("Workout read waits for 47 01") })
        ConnectionSetup.onWatchSetupDone()
        val result = withTimeoutOrNull(15_000) { read.await() }
        assertNotNull(result)
        assertEquals("001c0000000000000000", writes.first())
    }

    @Test fun afterAFailureAutomaticReadsWaitTenMinutesButTheButtonReads() = runBlocking {
        ConnectionSetup.onWatchSetupDone()
        assertTrue(H5600WorkoutIO.download()!!.error!!.contains("fail reason 1"))
        writes.clear()
        assertNull(H5600WorkoutIO.download())
        assertTrue(writes.toString(), writes.isEmpty())
        assertTrue(AppLog.recentEvents().any { it.contains("next automatic try in 10 min") })

        assertNotNull(H5600WorkoutIO.download(userAsked = true))
        assertEquals("001c0000000000000000", writes.first())

        writes.clear()
        now += H5600WorkoutIO.RETRY_AFTER_FAIL_MS + 1
        assertNotNull(H5600WorkoutIO.download())
        assertTrue(writes.isNotEmpty())
    }

    @Test fun aSecondAutomaticReadIsNotQueuedBehindAWaitingOne() = runBlocking {
        ConnectionSetup.onWatchSetupDone()
        WatchExchange.mutex.lock()
        val first = async(Dispatchers.IO) { H5600WorkoutIO.download() }
        delay(300)
        // Bounded: queued behind the held lock it would wait for ever (that was the failure).
        assertNull(withTimeoutOrNull(2_000) { H5600WorkoutIO.download() })
        assertTrue(AppLog.recentEvents().any { it.contains("one is already running or waiting") })
        WatchExchange.mutex.unlock()
        assertNotNull(withTimeoutOrNull(15_000) { first.await() })
        assertEquals(1, writes.count { it == "001c0000000000000000" })
    }
}
