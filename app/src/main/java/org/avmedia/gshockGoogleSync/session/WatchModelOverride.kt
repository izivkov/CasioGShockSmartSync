package org.avmedia.gshockGoogleSync.session

/**
 * GShockAPI 1.8.5 maps "GBX-H5600" (module 3586) to the button-connect model GBD, but it is the same
 * always-connected platform as the DW-H5600 (module 3516): it drops and reconnects about every 45 s by
 * design. The library resolves the model only from the exact name, so the app re-resolves such
 * watches as a DW-H5600 after connecting ([SessionEventBridge]) and keeps the real name for the UI.
 */
object WatchModelOverride {
    /** The name the library knows the H5600 platform by. */
    const val ALIAS = "DW-H5600"

    /** The real name of the connected watch when it was re-resolved as [ALIAS], else null. */
    @Volatile
    var realName: String? = null

    /** The name to give the library so it picks the right model behaviour. */
    fun effectiveLibraryName(name: String): String = if (isH5600Family(name)) ALIAS else name

    /** Always-connected watches: the H5600 family and the ECB. */
    fun isAlwaysConnected(name: String): Boolean = isH5600Family(name) || "ECB" in name

    fun isH5600Family(name: String): Boolean = "H5600" in name

    /** The name to show: the real one, also while the library believes it is [ALIAS]. */
    fun displayName(libraryName: String): String = realName ?: libraryName
}
