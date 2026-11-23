package org.avmedia.gshockapi.io

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * The watch link carries one exchange at a time. A LifeLog transfer holds it for the whole transfer and waits for
 * it; a raw or settings read takes it with tryLock and does not start (returns nothing) while a transfer runs.
 */
object WatchExchange {
    val mutex = Mutex()

    /** True while a LifeLog, workout or tide transfer runs; a test seam. */
    @Volatile var transferRunning: () -> Boolean = { H5600HealthIO.isActive || H5600WorkoutIO.isActive || TideTransferIO.isActive }

    /**
     * The library's settings reads ([CachedIO]) wait here: during a transfer their answers would reach the transfer, not
     * the read, and a caller with a limit asks again and again (owner log 2026-11-23_2309: `0x11` every 4 to 5 s).
     */
    suspend fun awaitNoTransfer(pollMs: Long = 100) {
        if (!transferRunning()) return
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("settings read waits: a transfer holds the link")
        while (transferRunning()) delay(pollMs)
    }
}
