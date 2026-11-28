package org.avmedia.gshockGoogleSync.ui.health

import CachedIO
import org.avmedia.gshockapi.model.StepCounterData

/**
 * The DW-H5600 LifeLog of the current connection. It is kept in [CachedIO] like every other value the library reads from
 * the watch, so it lives exactly as long as the connection: the library clears the cache when a connection is set up
 * ([org.avmedia.gshockapi.ble.IGShockManager]), and the first open of the Health screen in the next connection reads the
 * watch once. This object keeps no cache of its own; it is the one place that stores a transfer and says what happens
 * with it (the day merge, the history, the plugins).
 */
internal object H5600Session {
    private const val KEY = "H5600_LIFELOG"

    /** One LifeLog transfer at a time, shared by the Health screen and the plugins' reads. */
    val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Called when a LifeLog read has just been stored (starts the Home Assistant push). */
    @Volatile
    var onDownloaded: (() -> Unit)? = null

    /** Called with every stored transfer: the health history keeps what it decoded. */
    @Volatile
    var onStored: ((StepCounterData) -> Unit)? = null

    /**
     * Lays a new transfer over the stored day (set by the app: [org.avmedia.gshockGoogleSync.history.TodayHours] with the
     * stored hours). Runs off the main thread in [store]; a failure keeps the transfer as it came.
     */
    @Volatile
    var dayMerger: (StepCounterData) -> StepCounterData = { it }

    /** The last transfer of this connection; null when the connection has not read one yet. */
    val data: StepCounterData?
        get() = CachedIO.getOrNull(KEY)

    /** Stores a new transfer merged with the stored day, so the screen and Home Assistant get one full day. */
    suspend fun store(fresh: StepCounterData): StepCounterData {
        val merged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { dayMerger(fresh) }.getOrDefault(fresh)
        }
        CachedIO.put(KEY, merged)
        onStored?.invoke(merged)
        onDownloaded?.invoke()
        return merged
    }

    fun clear() {
        CachedIO.remove(KEY)
    }
}
