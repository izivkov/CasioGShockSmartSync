package org.avmedia.gshockGoogleSync.session

/**
 * A short window opened by the "Allow connection" action, kept per watch address. A connection from
 * that watch that starts while its window is open opens the main navigation, even when the watch
 * reports the always-connected button type. The first such connection uses up the window.
 */
object SetupWindow {
    private const val WINDOW_MS = 2 * 60_000L

    private val openUntil = mutableMapOf<String, Long>()

    fun open(address: String, now: Long = System.currentTimeMillis()) {
        synchronized(this) { openUntil[address.uppercase()] = now + WINDOW_MS }
    }

    /** Closes the window for [address] without a connection. */
    fun cancel(address: String) {
        synchronized(this) { openUntil.remove(address.uppercase()) }
    }

    /** True once for a connection from [address] that starts inside its window; the window is then used up. */
    fun consume(address: String, now: Long = System.currentTimeMillis()): Boolean =
        synchronized(this) {
            val until = openUntil[address.uppercase()] ?: return false
            openUntil.remove(address.uppercase())
            now < until
        }
}
