package org.avmedia.gshockapi

import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * For always-connected watches, limit the connection frequency to once every 6 hours.
 * Otherwise, frequent background auto-connections may block other watches or drain battery.
 */
object AlwaysConnectedWatchFilter {
    const val SIX_HOURS_IN_MS: Long = 6 * 3600 * 1000L

    private val lastConnectedTimes: MutableMap<String, Long> = mutableMapOf()
    private val allowOnceMap: MutableMap<String, Boolean> = mutableMapOf()

    fun setAllowOnce(watchName: String, allow: Boolean) {
        if (watchName.isBlank()) return
        val key = watchName.trim()
        synchronized(this) {
            allowOnceMap[key] = allow
        }
    }

    fun isAllowOnce(watchName: String): Boolean {
        if (watchName.isBlank()) return false
        val key = watchName.trim()
        return synchronized(this) { allowOnceMap[key] ?: false }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun connectionFilter(watchName: String): Boolean {
        if (watchName.isBlank()) {
            return true
        }

        val isAlwaysConnected = WatchInfo.isAlwaysConnected(watchName)
        if (!isAlwaysConnected) {
            // Not an always-connected watch - allow connection...
            return true
        }

        if (isAllowOnce(watchName)) {
            Timber.i("AlwaysConnectedWatchFilter: allow-once enabled for $watchName, overriding filter")
            updateConnectionTime(watchName)
            return true
        }

        val key = watchName.trim()
        val lastTime = synchronized(this) { lastConnectedTimes[key] }
        val now = System.currentTimeMillis()

        if (lastTime == null) {
            // Connected for the first time - allow connection...
            updateConnectionTime(watchName)
            return true
        }

        val elapsed = now - lastTime

        if (elapsed > SIX_HOURS_IN_MS) {
            // Last connected more than 6 hours ago - allow connection...
            updateConnectionTime(watchName)
            return true
        }

        // Last connected less than 6 hours ago - deny connection...
        Timber.d("AlwaysConnectedWatchFilter: denying connection to $watchName (last connected ${elapsed / 1000}s ago)")
        return false
    }

    fun updateConnectionTime(watchName: String) {
        if (watchName.isBlank()) return
        val key = watchName.trim()
        synchronized(this) {
            lastConnectedTimes[key] = System.currentTimeMillis()
            allowOnceMap[key] = false
        }
    }

    /** Clears connection history (primarily for unit testing or resets). */
    fun reset() {
        synchronized(this) {
            lastConnectedTimes.clear()
            allowOnceMap.clear()
        }
    }
}
