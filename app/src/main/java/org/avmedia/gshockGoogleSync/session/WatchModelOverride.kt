package org.avmedia.gshockGoogleSync.session

/**
 * Name-based questions the app asks before the library has resolved a watch (the pairing and first-page screens
 * only have the Bluetooth name). The library's model table maps "GBX-H5600" to the H5600 model itself, so nothing
 * is re-resolved any more.
 */
object WatchModelOverride {
    /** Always-connected watches: the H5600 family and the ECB. */
    fun isAlwaysConnected(name: String): Boolean = isH5600Family(name) || "ECB" in name

    fun isH5600Family(name: String): Boolean = "H5600" in name
}
