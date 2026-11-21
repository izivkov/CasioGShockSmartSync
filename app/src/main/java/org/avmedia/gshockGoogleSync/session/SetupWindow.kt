package org.avmedia.gshockGoogleSync.session

/**
 * A short window opened by the user's "Set up watch" action. A connection that starts while the
 * window is open opens the main navigation, even when the watch reports the always-connected button
 * type. The first such connection uses up the window.
 */
object SetupWindow {
    private const val WINDOW_MS = 2 * 60_000L

    private var openUntilMillis: Long = 0L

    fun open(now: Long = System.currentTimeMillis()) {
        synchronized(this) { openUntilMillis = now + WINDOW_MS }
    }

    /** True once for a connection that starts inside the window; the window is then used up. */
    fun consume(now: Long = System.currentTimeMillis()): Boolean =
        synchronized(this) {
            if (now < openUntilMillis) {
                openUntilMillis = 0L
                true
            } else {
                false
            }
        }
}
