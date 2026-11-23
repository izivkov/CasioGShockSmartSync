package org.avmedia.gshockGoogleSync.ui.actions

import java.util.concurrent.atomic.AtomicInteger

/** How many runs of watch actions are going on now; the background LifeLog read waits while any run. */
object WatchActions {
    private val running = AtomicInteger(0)

    val isRunning: Boolean get() = running.get() > 0

    suspend fun <T> running(block: suspend () -> T): T {
        running.incrementAndGet()
        try {
            return block()
        } finally {
            running.decrementAndGet()
        }
    }
}
