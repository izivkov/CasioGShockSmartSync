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

    private val exchanges = java.util.concurrent.atomic.AtomicInteger(0)

    /** True while a settings read or the time exchange runs ([settingsExchange]). */
    val settingsExchangeRunning: Boolean get() = exchanges.get() > 0

    @Volatile private var lastExchangeEndMs = 0L

    /** When the last LifeLog or workout transfer ended (after its END echo); 0 before the first. */
    @Volatile var lastTransferEndMs = 0L
        private set

    /** A transfer ended: the watch needs a moment before the next one starts (it refused a start 5 ms later with `03 01`). */
    fun onTransferEnded(nowMs: Long = System.currentTimeMillis()) {
        lastTransferEndMs = nowMs
    }

    /** The pause after a transfer before the next one starts. */
    const val SETTLE_AFTER_TRANSFER_MS = 3_000L

    /** Waits until [SETTLE_AFTER_TRANSFER_MS] passed since the last transfer ended. */
    suspend fun awaitSettledAfterTransfer() {
        val left = lastTransferEndMs + SETTLE_AFTER_TRANSFER_MS - System.currentTimeMillis()
        if (left <= 0) return
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("transfer waits ${(left + 999) / 1000} s: the last transfer just ended")
        delay(left)
    }

    /** Marks [block] as a settings or time exchange: a transfer does not start while one runs. */
    suspend fun <T> settingsExchange(block: suspend () -> T): T {
        exchanges.incrementAndGet()
        try {
            return block()
        } finally {
            lastExchangeEndMs = System.currentTimeMillis()
            exchanges.decrementAndGet()
        }
    }

    /**
     * Before a transfer starts: waits until no settings or time exchange ran for [quietMs] (the time exchange is several reads
     * in a row, each counted by [CachedIO]), at most [maxMs]. A workout start during a time exchange got `03 01` or a flood of `00 01 04` (busy).
     * Returns false when one still ran at [maxMs] (the transfer goes on; its start answers busy or refuses, and is tried again).
     */
    suspend fun awaitNoSettingsExchange(quietMs: Long = 1_000L, maxMs: Long = 30_000L, pollMs: Long = 50L): Boolean {
        fun quiet() = exchanges.get() == 0 && System.currentTimeMillis() - lastExchangeEndMs >= quietMs
        if (quiet()) return true
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("transfer waits: a settings or time exchange runs")
        return kotlinx.coroutines.withTimeoutOrNull(maxMs) {
            while (!quiet()) delay(pollMs)
            true
        } ?: false
    }

    /**
     * The library's settings reads ([CachedIO]) wait here: during a transfer their answers would reach the transfer, not
     * the read, and a caller with a limit asks again and again (`0x11` every 4 to 5 s).
     */
    suspend fun awaitNoTransfer(pollMs: Long = 100) {
        if (!transferRunning()) return
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.log("settings read waits: a transfer holds the link")
        while (transferRunning()) delay(pollMs)
    }
}
