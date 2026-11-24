package org.avmedia.smartsync.plugin.api

/**
 * The version of this interface. A plugin states the version it was built against ([WatchPlugin.apiVersion], which is
 * this constant as compiled into the plugin); the host activates it only when [compatible].
 *
 * Rules, until 1.0: a new patch (0.1.x) only adds (README "What may change in a compatible release"); a new minor
 * (0.2.0) may break. So a 0.x host runs a plugin of its own minor whose patch is not newer than its own (a newer patch
 * may call what this host does not have). From 1.0: a new minor only adds, a new major may break; a host runs every
 * plugin of its major whose minor is not newer than its own.
 */
object PluginApi {
    const val VERSION = "0.1.0"

    /** Major, minor and patch of [version] ("0.1" gives 0, 1, 0); null for a version that is not "major.minor[.patch]". */
    fun parse(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split('.')
        if (parts.size !in 2..3) return null
        val n = parts.map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return null }
        return Triple(n[0], n[1], n.getOrElse(2) { 0 })
    }

    /** Whether a host of [hostVersion] can run a plugin built against [pluginVersion]. */
    fun compatible(pluginVersion: String, hostVersion: String = VERSION): Boolean {
        val (pMajor, pMinor, pPatch) = parse(pluginVersion) ?: return false
        val (hMajor, hMinor, hPatch) = parse(hostVersion) ?: return false
        if (pMajor != hMajor) return false
        return if (hMajor == 0) pMinor == hMinor else pMinor <= hMinor
    }

    /** Whether [version] is [since] or newer (false for a version that cannot be read). */
    fun atLeast(version: String, since: String): Boolean {
        val v = parse(version) ?: return false
        val s = parse(since) ?: return false
        return compareValuesBy(v, s, { it.first }, { it.second }, { it.third }) >= 0
    }
}
