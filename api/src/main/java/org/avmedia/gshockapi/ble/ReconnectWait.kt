package org.avmedia.gshockapi.ble

import kotlinx.coroutines.delay

/** A write started while the watch's link is away waits for it to come back, up to [WINDOW_MS]. */
object ReconnectWait {
    const val WINDOW_MS = 3 * 60 * 1000L
    const val POLL_MS = 250L
    const val NOT_CONNECTED_MESSAGE = "The watch is not connected. Try again when it is back."

    /** True when the link came up within [timeoutMs]. */
    suspend fun awaitLink(isUp: () -> Boolean, timeoutMs: Long = WINDOW_MS, pollMs: Long = POLL_MS): Boolean {
        var waited = 0L
        while (!isUp()) {
            if (waited >= timeoutMs) return false
            delay(pollMs)
            waited += pollMs
        }
        return true
    }
}
