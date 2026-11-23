package org.avmedia.gshockGoogleSync.ui.health

/**
 * Opening the Health screen starts one read of the watch by itself (no Update button): not when one is running, and
 * not again within [MIN_GAP_MS] of the last one started on open. Process-wide ([shared]), so a screen recreated by
 * the navigation counts as the same open. Pure, the clock and the running flag are passed in.
 */
class HealthOpenRead(
    private val clock: () -> Long = System::currentTimeMillis,
    private val running: () -> Boolean = { H5600Session.inFlight.get() },
) {
    @Volatile private var lastStartMs: Long? = null

    /** True when this open should start a read; records the start. */
    @Synchronized
    fun tryStart(): Boolean {
        if (running()) return false
        val now = clock()
        val last = lastStartMs
        if (last != null && now - last < MIN_GAP_MS) return false
        lastStartMs = now
        return true
    }

    companion object {
        const val MIN_GAP_MS = 60_000L
        val shared = HealthOpenRead()
    }
}
