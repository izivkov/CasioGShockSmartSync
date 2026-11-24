package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.smartsync.plugin.api.Capability
import org.avmedia.smartsync.plugin.api.PluginAccess

/** How far a plugin works with one watch ([PluginAccess.requires], [PluginAccess.uses]). Android-free. */
sealed interface WatchSupport {
    /** Everything the plugin uses. */
    data object Full : WatchSupport

    /** It works, without [missing]. */
    data class Partial(val missing: Set<Capability>) : WatchSupport

    /** The watch lacks [missing], which the plugin requires: it gets nothing for this watch. */
    data class NotSupported(val missing: Set<Capability>) : WatchSupport

    companion object {
        /** For a watch with [caps]; an empty set (a watch never connected) counts as not known yet: [Full]. */
        fun of(access: PluginAccess, caps: Set<Capability>): WatchSupport {
            if (caps.isEmpty()) return Full
            val needed = access.requires - caps
            if (needed.isNotEmpty()) return NotSupported(needed)
            val missing = access.uses - caps
            return if (missing.isEmpty()) Full else Partial(missing)
        }
    }
}
