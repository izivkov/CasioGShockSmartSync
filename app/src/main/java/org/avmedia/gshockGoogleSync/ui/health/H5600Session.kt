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

    /** Opening the Health screen reads again only after this (the background read keeps it newer). */
    private const val FRESH_MS = 2 * 60 * 60_000L

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        data != null && now - storedAtMs < FRESH_MS

    /** Called when a LifeLog read has just been stored (starts the Home Assistant push). */
    @Volatile
    var onDownloaded: (() -> Unit)? = null

    /** Called with every stored transfer: the health history keeps what it decoded. */
    @Volatile
    var onStored: ((StepCounterData) -> Unit)? = null

    @Volatile
    var data: StepCounterData? = null
        set(value) {
            field = value
            storedAtMs = if (value != null) System.currentTimeMillis() else 0L
            if (value != null) {
                onStored?.invoke(value)
                onDownloaded?.invoke()
            }
        }

    /**
     * Lays a new transfer over the stored day (set by the app: [org.avmedia.gshockGoogleSync.history.TodayHours] with the
     * stored hours). Runs off the main thread in [store]; a failure keeps the transfer as it came.
     */
    @Volatile
    var dayMerger: (StepCounterData) -> StepCounterData = { it }

    /** Stores a new transfer merged with the stored day, so the screen and Home Assistant get one full day. */
    suspend fun store(fresh: StepCounterData): StepCounterData {
        val merged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { dayMerger(fresh) }.getOrDefault(fresh)
        }
        data = merged
        return merged
    }

    fun clear() {
        data = null
    }
}
