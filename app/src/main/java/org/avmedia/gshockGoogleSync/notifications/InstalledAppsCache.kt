package org.avmedia.gshockGoogleSync.notifications

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The installed apps for the picker, read once per app session and off the main thread: [InstalledApps.list] asks the
 * package manager for every app's name and froze the screen for seconds when it ran in the composition.
 */
class SessionListCache<T>(private val io: CoroutineDispatcher = Dispatchers.IO, private val load: () -> List<T>) {
    private val lock = Mutex()
    @Volatile private var cached: List<T>? = null

    suspend fun get(): List<T> = load()
}

object InstalledAppsCache {
    @Volatile private var cache: SessionListCache<AppEntry>? = null

    suspend fun get(context: android.content.Context): List<AppEntry> {
        val app = context.applicationContext
        val c = cache ?: synchronized(this) { cache ?: SessionListCache { InstalledApps.list(app) }.also { cache = it } }
        return c.get()
    }
}
