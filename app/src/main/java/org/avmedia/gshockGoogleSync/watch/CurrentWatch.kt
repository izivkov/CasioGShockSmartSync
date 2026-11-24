package org.avmedia.gshockGoogleSync.watch

import android.content.Context
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/** Which watch the per-watch stores read and write for right now. */
object CurrentWatch {
    /** The connected watch's key; "" when no watch is connected. */
    fun key(): String = WatchKeys.of(runCatching { org.avmedia.gshockapi.WatchInfo.getAddress() }.getOrDefault(""))

    /** The connected watch, else the last one that was connected (screens opened without a watch). */
    fun keyOrLast(context: Context): String =
        key().ifBlank { WatchKeys.of(LocalDataStorage.get(context, "LastDeviceAddress", "").orEmpty()) }
}
