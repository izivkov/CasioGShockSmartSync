package org.avmedia.gshockGoogleSync.homeassistant

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.avmedia.gshockapi.log.LogSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CallRetryTest {
    private val waits = ArrayList<Long>()
    private val lines = ArrayList<String>()
    private var now = 1_000_000L

    @Before fun install() {
        CallRetry.sleeper = { waits += it }
        CallRetry.writer = { _, text -> lines += text }
        CallRetry.clock = { now }
        CallRetry.resetFailureWindow()
    }

    @After fun restore() {
        CallRetry.sleeper = { Thread.sleep(it) }
        CallRetry.writer = org.avmedia.gshockapi.log.AppLog::log
        CallRetry.clock = System::currentTimeMillis
    }

    private fun run(vararg answers: Int?, quiet: Set<Int> = emptySet(), retries: Boolean = true): Int {
        var n = 0
        CallRetry.run(LogSource.HomeAssistant, "W", retries, quiet) {
            val s = answers[minOf(n, answers.size - 1)]
            n++
            CallRetry.Attempt(s, s, if (s == null) "host not reachable" else null)
        }
        return n
    }

    @Test fun networkErrorTwiceThenSuccessIsThreeAttemptsAndOneSentLine() {
        assertEquals(3, run(null, null, 200))
        assertEquals(listOf(2_000L, 5_000L), waits)
        assertEquals(listOf("W: sent after 3 tries"), lines)
    }

    @Test fun fourFailuresIsFourAttemptsAndOneFailureLine() {
        assertEquals(4, run(null))
        assertEquals(listOf(2_000L, 5_000L, 15_000L), waits)
        assertEquals(listOf("W: failed after 4 tries: host not reachable"), lines)
    }

    @Test fun http400IsOneAttemptLoggedAtOnce() {
        assertEquals(1, run(400))
        assertTrue(waits.isEmpty())
        assertEquals(listOf("W: failed after 1 try: HTTP 400"), lines)
    }

    @Test fun retryableStatusesAre408429And5xx() {
        for (s in listOf(408, 429, 500, 503)) { waits.clear(); assertEquals("status $s", 4, run(s)) }
        for (s in listOf(400, 401, 403, 404, 410)) { waits.clear(); assertEquals("status $s", 1, run(s)) }
    }

    @Test fun firstTrySuccessLogsNothing() {
        assertEquals(1, run(200))
        assertTrue(lines.isEmpty())
    }

    @Test fun quietStatusIsNotRetriedAndNotLogged() {
        assertEquals(1, run(410, quiet = setOf(410)))
        assertTrue(waits.isEmpty() && lines.isEmpty())
    }

    @Test fun noRetriesForTheTestButton() {
        assertEquals(1, run(null, retries = false))
        assertEquals(listOf("W: failed after 1 try: host not reachable"), lines)
    }

    @Test fun reasonsAreShortWordsNeverTheMessage() {
        assertEquals("host not reachable", CallRetry.reasonOf(null, UnknownHostException("a.example/secret")))
        assertEquals("host not reachable", CallRetry.reasonOf(null, ConnectException("x")))
        assertEquals("timeout", CallRetry.reasonOf(null, SocketTimeoutException("t")))
        assertEquals("HTTP 503", CallRetry.reasonOf(503, null))
        assertFalse(CallRetry.reasonOf(null, IllegalStateException("token=SECRET")).contains("SECRET"))
    }

    // WorkManager runs the job up to 3 times; each run is a call chain with the same failure.
    @Test fun threeWorkManagerRunsOfTheSameFailureWriteOneLine() {
        repeat(3) { run(null); now += 60_000 }
        assertEquals(listOf("W: failed after 4 tries: host not reachable"), lines)
    }

    @Test fun theSameFailureIsWrittenAgainAfterTheWindowAndADifferentOneAtOnce() {
        run(null)
        now += CallRetry.REPEAT_WINDOW_MS + 1
        run(null)
        run(503)
        assertEquals(3, lines.size)
    }

    @Test fun aRecoveryOpensANewWindow() {
        run(null)
        run(200)
        run(null)
        assertEquals(2, lines.size)
    }
}
