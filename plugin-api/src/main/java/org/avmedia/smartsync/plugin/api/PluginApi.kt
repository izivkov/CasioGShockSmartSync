package org.avmedia.smartsync.plugin.api

/**
 * The version of this interface. A plugin states the version it was built against ([WatchPlugin.apiVersion], which is
 * this constant as compiled into the plugin); the host activates it only when [compatible].
 *
 * Rules (semantic versioning): a new major version may break plugins. While the major is 0, a new minor may break
 * them too, so 0.x plugins need the same minor. From 1.0 a host runs every plugin of its major whose minor is not
 * newer than its own (a newer minor may call what this host does not have).
 */
object PluginApi {
    const val VERSION = "0.1.0"

    /** Major and minor of [version] ("0.1.0" gives 0 to 1); null for a version that is not "major.minor[.patch]". */
    fun parse(version: String): Pair<Int, Int>? {
        val parts = version.trim().split('.')
        if (parts.size !in 2..3) return null
        val n = parts.map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return null }
        return n[0] to n[1]
    }

    /** Whether a host of [hostVersion] can run a plugin built against [pluginVersion]. */
    fun compatible(pluginVersion: String, hostVersion: String = VERSION): Boolean {
        val (pMajor, pMinor) = parse(pluginVersion) ?: return false
        val (hMajor, hMinor) = parse(hostVersion) ?: return false
        if (pMajor != hMajor) return false
        return if (hMajor == 0) pMinor == hMinor else pMinor <= hMinor
    }
}
