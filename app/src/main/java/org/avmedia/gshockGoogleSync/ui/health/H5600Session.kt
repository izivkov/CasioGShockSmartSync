package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.StepCounterData

/**
 * The DW-H5600 LifeLog result, kept for the life of the app session so that opening the Health
 * screen does not start a new watch transfer. Cleared only by [clear].
 */
internal object H5600Session {
    /** One LifeLog transfer at a time, shared by the background read and the Update button. */
    val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /** When [data] was stored (ms), 0 when empty. */
    @Volatile
    var storedAtMs: Long = 0L

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        data != null && now - storedAtMs < HealthReadScheduler.INTERVAL_MS

    /** Called when a LifeLog read has just been stored (the "health data downloaded" webhook trigger). */
    @Volatile
    var onDownloaded: (() -> Unit)? = null

    @Volatile
    var data: StepCounterData? = null
        set(value) {
            field = value
            storedAtMs = if (value != null) System.currentTimeMillis() else 0L
            if (value != null) onDownloaded?.invoke()
        }

    fun clear() {
        data = null
    }
}
