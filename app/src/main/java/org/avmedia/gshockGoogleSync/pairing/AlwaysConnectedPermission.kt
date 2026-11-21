package org.avmedia.gshockGoogleSync.pairing

/**
 * Permission for an always-connected watch to connect by itself, kept per watch address. "Allow
 * connection" on a watch grants it for that watch only, and the next successful connection of that
 * watch uses it up. Attempts from any other watch are not affected.
 *
 * Held in memory, so it has to be granted again after the app restarts.
 */
object AlwaysConnectedPermission {
    private val granted = mutableSetOf<String>()

    fun grant(address: String) {
        synchronized(this) { granted.add(address.uppercase()) }
    }

    fun use(address: String) {
        synchronized(this) { granted.remove(address.uppercase()) }
    }

    fun isGranted(address: String): Boolean =
        synchronized(this) { granted.contains(address.uppercase()) }
}
