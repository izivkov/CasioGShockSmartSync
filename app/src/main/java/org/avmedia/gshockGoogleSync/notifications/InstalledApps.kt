package org.avmedia.gshockGoogleSync.notifications

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String)

object InstalledApps {
    private var cache: SessionListCache<AppEntry>? = null

    /** Apps with a launcher icon, sorted by name. Slow (reads every app's label): call it through [get]. */
    fun list(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** The installed apps, read once per app session and off the main thread. */
    suspend fun get(context: Context): List<AppEntry> {
        val appContext = context.applicationContext
        val listCache = synchronized(this) {
            cache ?: SessionListCache { list(appContext) }.also { cache = it }
        }
        return listCache.get()
    }

    /** The apps whose name or package contains [query], ignoring case; all of them for a blank query. */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.filter { q in it.label.lowercase() || q in it.packageName.lowercase() }
    }
}

/** A list loaded once, on [io], the first time it is asked for. */
class SessionListCache<T>(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val load: () -> List<T>,
) {
    private val lock = Mutex()

    @Volatile
    private var cached: List<T>? = null

    suspend fun get(): List<T> = cached ?: lock.withLock {
        cached ?: withContext(io) { load() }.also { cached = it }
    }
}
