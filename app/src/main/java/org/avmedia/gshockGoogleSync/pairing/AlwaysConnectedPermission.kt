package org.avmedia.gshockGoogleSync.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Permission for an always-connected watch to connect by itself, kept per watch address. Turning the
 * switch on grants it for that watch only, and the next successful connection of that watch uses it up.
 * Attempts from any other watch are not affected.
 *
 * Held in memory, so it is off again after the app restarts.
 */
object AlwaysConnectedPermission {
    private val grantedAddresses = mutableSetOf<String>()
    private val _granted = MutableStateFlow<Set<String>>(emptySet())

    /** Addresses (upper case) whose connection is currently allowed. */
    val granted: StateFlow<Set<String>> = _granted

    fun grant(address: String) {
        synchronized(this) {
            grantedAddresses.add(address.uppercase())
            _granted.value = grantedAddresses.toSet()
        }
    }

    fun use(address: String) {
        synchronized(this) {
            grantedAddresses.remove(address.uppercase())
            _granted.value = grantedAddresses.toSet()
        }
    }

    fun isGranted(address: String): Boolean =
        synchronized(this) { grantedAddresses.contains(address.uppercase()) }
}
