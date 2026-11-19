package org.avmedia.gshockGoogleSync.pairing

import org.avmedia.gshockapi.WatchInfo

/**
 * The DW-H5600 connects by itself when it appears. The app does not want that connection unless
 * the user has started a sync: each connection puts the watch on its lock screen.
 *
 * A sync arms the policy for a short window. While it is not armed, "device appeared" events for
 * the DW-H5600 are ignored, so there is no connection and no reconnect loop. The policy does not
 * change [WatchInfo.alwaysConnected], which describes the watch's behaviour, not the app's.
 */
internal object H5600ConnectPolicy {
    private const val ARM_WINDOW_MS = 5 * 60_000L

    @Volatile
    private var armedUntilMillis: Long = 0L

    /** Called when the user starts a sync, for example by opening the Health screen. */
    fun arm() {
        armedUntilMillis = System.currentTimeMillis() + ARM_WINDOW_MS
    }

    /** Called when the sync finishes, so the next unsolicited connection is ignored again. */
    fun disarm() {
        armedUntilMillis = 0L
    }

    /** True when a "device appeared" event may start a connection. Other watch models are not affected. */
    fun allowsWatchInitiatedConnection(): Boolean =
        WatchInfo.model != WatchInfo.WatchModel.DW_H5600 || System.currentTimeMillis() < armedUntilMillis
}
