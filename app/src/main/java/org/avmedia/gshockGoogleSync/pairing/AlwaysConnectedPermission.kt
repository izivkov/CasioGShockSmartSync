package org.avmedia.gshockGoogleSync.pairing

/**
 * Permission for an always-connected watch (the DW-H5600) to connect by itself. The "Allow connection"
 * button grants it, and the next successful connection uses it up. While it is not granted, the
 * watch's own connection attempts are ignored.
 *
 * Held in memory, so it has to be granted again after the app restarts.
 */
object AlwaysConnectedPermission {
    @Volatile
    private var granted = false

    fun grant() {
        granted = true
    }

    fun use() {
        granted = false
    }

    fun isGranted(): Boolean = granted
}
