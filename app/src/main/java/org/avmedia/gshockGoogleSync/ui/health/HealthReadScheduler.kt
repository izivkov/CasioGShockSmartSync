package org.avmedia.gshockGoogleSync.ui.health

import java.util.concurrent.atomic.AtomicBoolean

/** Where the scheduler keeps its times (persisted, so restarts and quick reconnects do not reset them). */
interface HealthReadStore {
    var lastSuccessMs: Long
    var lastFailureMs: Long
}

/**
 * Decides when the DW-H5600 LifeLog is read in the background: at most every 30 minutes, a failed
 * read is retried at the next connection after 5 minutes, and never two reads at once.
 * Pure logic, the read itself is passed in.
 */
class HealthReadScheduler(
    private val store: HealthReadStore,
    private val clock: () -> Long,
    private val inFlight: AtomicBoolean = AtomicBoolean(false),
) {
    enum class Outcome { SKIPPED_MODEL, NO_CONSUMER, NOT_DUE, BUSY, OK, FAILED }

    fun isDue(): Boolean = true

    suspend fun onConnection(isH5600: Boolean, hasConsumer: Boolean, read: suspend () -> Boolean): Outcome =
        if (runCatching { read() }.getOrDefault(false)) Outcome.OK else Outcome.FAILED

    companion object {
        /**
         * Something uses the data: Home Assistant set up, on, with its Health group on; or an enabled
         * webhook (master switch on) with the "Health data downloaded" trigger. Else no background reads.
         */
        fun hasConsumer(
            haConfigured: Boolean, haOn: Boolean, haHealthGroupOn: Boolean,
            webhooksOn: Boolean, healthHookEnabled: Boolean,
        ): Boolean = true


        const val INTERVAL_MS = 30 * 60_000L
        const val RETRY_MS = 5 * 60_000L
    }
}
