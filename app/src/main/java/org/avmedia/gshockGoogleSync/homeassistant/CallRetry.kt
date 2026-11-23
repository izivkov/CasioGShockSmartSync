package org.avmedia.gshockGoogleSync.homeassistant

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource

/**
 * The one retry rule for outgoing Home Assistant calls: a network error, a timeout, HTTP 408, 429 or
 * 5xx is tried again up to 3 more times after 2 s, 5 s and 15 s; any other answer is final. Then one line goes to the
 * app log. Blocking: runs on the caller's background thread, never under the watch exchange lock. Nothing is queued
 * beyond these tries. Log lines hold [what] and a short reason only, never a URL, header or token.
 */
object CallRetry {
    val DELAYS_MS: List<Long> = listOf(2_000L, 5_000L, 15_000L)

    /** WorkManager runs of one queued job, the first included. */
    const val MAX_RUNS = 3

    /** No response (offline, timeout, DNS) or a 5xx/429/408 answer is worth another try; other answers are final. */
    fun isRetryable(status: Int?): Boolean =
        status == null || status in 500..599 || status == 429 || status == 408

    @Volatile var sleeper: (Long) -> Unit = { Thread.sleep(it) }
    @Volatile var writer: (LogSource, String) -> Unit = AppLog::log
    @Volatile var clock: () -> Long = System::currentTimeMillis

    /**
     * WorkManager runs a failed job again (up to 3 runs, 30 s and 60 s apart), and each run is a new call chain here.
     * The same failure line is written once per [REPEAT_WINDOW_MS]: one line per failed call chain of the job.
     */
    const val REPEAT_WINDOW_MS = 10 * 60 * 1000L
    private val lastFailure = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun resetFailureWindow() = lastFailure.clear()

    private fun failureLine(source: LogSource, line: String) {
        val key = "${source.label}|$line"
        val now = clock()
        val before = lastFailure.put(key, now)
        if (before != null && now - before < REPEAT_WINDOW_MS) {
            lastFailure[key] = before // the window counts from the first line
            return
        }
        writer(source, line)
    }

    /** One try: [status] is null when no answer came, [reason] says why in a few words (null for an answer). */
    class Attempt<T>(val value: T, val status: Int?, val reason: String? = null) {
        val ok: Boolean get() = status != null && status in 200..299
    }

    /**
     * Runs [call] up to 1 + [retries] times. Logs a failure line ("what: failed after N tries: reason") unless the status
     * is in [quiet] (410 from Home Assistant goes to the re-register check, not to the log), and a success after
     * a retry ("what: sent after N tries"). A first-try success logs nothing.
     */
    fun <T> run(
        source: LogSource,
        what: String,
        retries: Boolean = true,
        quiet: Set<Int> = emptySet(),
        call: () -> Attempt<T>,
    ): T {
        val waits = if (retries) DELAYS_MS else emptyList()
        var tries = 0
        while (true) {
            val a = call()
            tries++
            if (a.ok) {
                lastFailure.keys.removeAll { it.startsWith("${source.label}|$what:") } // a recovery opens a new window
                if (tries > 1) writer(source, "$what: sent after $tries tries")
                return a.value
            }
            if (CallRetry.isRetryable(a.status) && tries <= waits.size) {
                sleeper(waits[tries - 1])
                continue
            }
            if (a.status == null || a.status !in quiet) (if (retries) ::failureLine else writer)(source, "$what: failed after $tries ${if (tries == 1) "try" else "tries"}: ${a.reason ?: reasonOf(a.status, null)}")
            return a.value
        }
    }

    /** A few words for a failure. Never the exception message: it can hold a URL. */
    fun reasonOf(status: Int?, error: Throwable?): String = when {
        status != null -> "HTTP $status"
        error is UnknownHostException || error is ConnectException || error is NoRouteToHostException -> "host not reachable"
        error is InterruptedIOException -> "timeout"
        error is SSLException -> "TLS error"
        error != null -> error.javaClass.simpleName
        else -> "no answer"
    }
}
