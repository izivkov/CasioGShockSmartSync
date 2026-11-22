package org.avmedia.gshockGoogleSync.notifications

import android.content.Context
import android.content.Intent

data class AppEntry(val packageName: String, val label: String)

object InstalledApps {
    /** Apps with a launcher icon, by name. Needs the launcher `<queries>` entry of the manifest to see them on Android 11 and up. */
    fun list(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** The apps whose name or package contains [query], ignoring case; all of them for a blank query. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) apps else apps.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
    }
}
