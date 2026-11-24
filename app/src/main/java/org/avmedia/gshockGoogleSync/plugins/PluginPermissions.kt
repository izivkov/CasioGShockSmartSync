package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.smartsync.plugin.api.AndroidPermission
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess

/**
 * The Android permissions a plugin needs: its own ([PluginAccess.androidPermissions]) and the location permission when
 * it reads the phone's location through the host ([DataUse.PHONE_LOCATION]). Android-free.
 */
object PluginPermissions {
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    fun required(access: PluginAccess): List<AndroidPermission> =
        (access.androidPermissions + listOfNotNull(
            AndroidPermission(COARSE_LOCATION, "the phone's coarse location").takeIf { false },
        )).distinctBy { it.name }

    /** Those of [required] that [granted] says no to; [ownCheck] (the plugin's own answer) wins where it has one. */
    fun missing(required: List<AndroidPermission>, granted: (String) -> Boolean, ownCheck: Set<String>? = null): List<AndroidPermission> =
        required.filter { p -> if (ownCheck != null) p.name in ownCheck else !granted(p.name) }

    /** "Needs permission: the phone's coarse location"; null when nothing is missing. */
    fun line(missing: List<AndroidPermission>): String? =
        missing.takeIf { it.isNotEmpty() }?.let { m -> "Needs permission: " + m.joinToString { it.reason } }
}
