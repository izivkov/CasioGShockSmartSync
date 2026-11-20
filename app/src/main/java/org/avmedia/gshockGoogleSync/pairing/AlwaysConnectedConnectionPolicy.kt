package org.avmedia.gshockGoogleSync.pairing

import android.content.Context
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/**
 * Limits automatic connections to an always-connected watch (the DW-H5600) to one every six hours.
 * A user can allow one connection now from the initial screen.
 *
 * This is an app policy, so it lives here rather than in the API. History and switch state are
 * stored per MAC address, so they survive app restarts.
 */
object AlwaysConnectedConnectionPolicy {
    const val SIX_HOURS_IN_MS: Long = 6 * 3600 * 1000L

    private const val LAST_CONNECTED_KEY = "AlwaysConnected.LastConnected."
    private const val ALLOW_ONCE_KEY = "AlwaysConnected.AllowOnce."

    /** True when a watch that appeared may be connected automatically. */
    fun connectionAllowed(
        context: Context,
        address: String,
        isAlwaysConnected: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        decide(
            isAlwaysConnected = isAlwaysConnected,
            allowOnce = isAllowOnce(context, address),
            lastConnectedMillis = lastConnected(context, address),
            now = now,
        )

    /** Records a connection, and clears the allow-once flag because it has been used. */
    fun recordConnection(context: Context, address: String, now: Long = System.currentTimeMillis()) {
        LocalDataStorage.put(context, LAST_CONNECTED_KEY + key(address), now.toString())
        setAllowOnce(context, address, false)
    }

    fun isAllowOnce(context: Context, address: String): Boolean =
        LocalDataStorage.get(context, ALLOW_ONCE_KEY + key(address), "false").toBoolean()

    fun setAllowOnce(context: Context, address: String, allow: Boolean) {
        LocalDataStorage.put(context, ALLOW_ONCE_KEY + key(address), allow.toString())
    }

    private fun lastConnected(context: Context, address: String): Long? =
        LocalDataStorage.get(context, LAST_CONNECTED_KEY + key(address))?.toLongOrNull()

    private fun key(address: String): String = address.trim().uppercase()

    /** The decision itself, with no storage, so it can be unit-tested. */
    internal fun decide(
        isAlwaysConnected: Boolean,
        allowOnce: Boolean,
        lastConnectedMillis: Long?,
        now: Long,
    ): Boolean {
        if (!isAlwaysConnected) return true
        if (allowOnce) return true
        if (lastConnectedMillis == null) return true
        return now - lastConnectedMillis > SIX_HOURS_IN_MS
    }
}
