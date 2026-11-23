package org.avmedia.gshockGoogleSync.ui.health

import java.util.concurrent.atomic.AtomicBoolean

/** Where the scheduler keeps its times (persisted, so restarts and quick reconnects do not reset them). */
interface HealthReadStore {
    var lastSuccessMs: Long
    var lastFailureMs: Long
}

/**
 * Decides when the DW-H5600 LifeLog is read in the background: at most every [intervalMs] (the setting, 15 minutes by
 * default, 0 = off), a failed read is retried after 5 minutes, never two reads at once and never while [busy] (another
 * watch exchange or a watch action runs). Called on each connection and on the periodic tick. Pure logic, the read
 * itself is passed in.
 */
class HealthReadScheduler(
    private val store: HealthReadStore,
    private val clock: () -> Long,
    private val inFlight: AtomicBoolean = AtomicBoolean(false),
    private val intervalMs: () -> Long = { DEFAULT_INTERVAL_MS },
    private val busy: () -> Boolean = { false },
) {
    enum class Outcome { SKIPPED_MODEL, NO_CONSUMER, OFF, NOT_DUE, BUSY, OK, FAILED }

    fun isDue(): Boolean {
        val now = clock()
        if (store.lastSuccessMs != 0L && now - store.lastSuccessMs < intervalMs()) return false
        if (store.lastFailureMs != 0L && now - store.lastFailureMs < RETRY_MS) return false
        return true
    }

    /** Call on every connection. [read] returns true when the LifeLog was read and stored. */
    suspend fun onConnection(isH5600: Boolean, hasConsumer: Boolean, read: suspend () -> Boolean): Outcome {
        if (!isH5600) return Outcome.SKIPPED_MODEL
        if (!hasConsumer) return Outcome.NO_CONSUMER
        // RED CHECK: no off
        if (!isDue()) return Outcome.NOT_DUE
        // RED CHECK: busy ignored
        if (!inFlight.compareAndSet(false, true)) return Outcome.BUSY
        try {
            val ok = runCatching { read() }.getOrDefault(false)
            if (ok) {
                store.lastSuccessMs = clock()
                store.lastFailureMs = 0L
            } else {
                store.lastFailureMs = clock()
            }
            return if (ok) Outcome.OK else Outcome.FAILED
        } finally {
            inFlight.set(false)
        }
    }

    companion object {
        /**
         * Something uses the data: Home Assistant set up, on, with its Health group on; or an enabled
         * webhook (master switch on) with the "Health data downloaded" trigger. Else no background reads.
         */
        fun hasConsumer(
            haConfigured: Boolean, haOn: Boolean, haHealthGroupOn: Boolean,
            webhooksOn: Boolean, healthHookEnabled: Boolean,
        ): Boolean = (haConfigured && haOn && haHealthGroupOn) || (webhooksOn && healthHookEnabled)


        const val DEFAULT_INTERVAL_MS = 2 * 60 * 60_000L // RED CHECK: old period
        const val RETRY_MS = 5 * 60_000L

        /** The choices of the setting, in minutes; 0 = off. */
        val CHOICES_MIN = listOf(15, 30, 60, 0)
    }
}
