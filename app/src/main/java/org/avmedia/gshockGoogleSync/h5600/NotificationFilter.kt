package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import android.content.Intent

/**
 * Per-app allowlist for notifications forwarded to the watch (replaces the CASIO app's
 * "select apps" setting). Disabled = stock Smart Sync behaviour (forward everything).
 */
object NotificationFilter {
    data class Config(val enabled: Boolean = false, val packages: Set<String> = emptySet())
    data class AppEntry(val packageName: String, val label: String)

    private const val PREFS = org.avmedia.gshockGoogleSync.h5600.H5600Prefs.NAME
    private const val KEY_ENABLED = "notif_filter_enabled"
    private const val KEY_PACKAGES = "notif_filter_packages"

    fun load(context: Context): Config =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { p ->
            Config(
                enabled = p.getBoolean(KEY_ENABLED, false),
                packages = p.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet(),
            )
        }

    fun save(context: Context, cfg: Config) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, cfg.enabled)
            .putStringSet(KEY_PACKAGES, cfg.packages)
            .apply()
    }

    fun allows(context: Context, packageName: String): Boolean =
        load(context).let { !it.enabled || packageName in it.packages }

    /** Launchable apps, sorted by label (needs the LAUNCHER <queries> entry in the manifest). */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
