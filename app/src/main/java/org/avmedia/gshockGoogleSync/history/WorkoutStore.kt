package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource
import android.content.Context
import org.avmedia.gshockapi.model.WorkoutSession
import timber.log.Timber

/**
 * Keeps downloaded workouts in [HistoryDb] and remembers the last one for the Home Assistant sensors.
 * The watch is told "fetched" only when [save] returned true, so a failed save is read again next time.
 */
object WorkoutStore {
    /** The newest workout of this app session; null until one was downloaded. */
    @Volatile var last: WorkoutSession? = null
        private set

    /** Called after each stored workout (starts the Home Assistant push). */
    @Volatile var onDownloaded: (() -> Unit)? = null

    /** Stores [session]; true when it is in the database. */
    suspend fun save(context: Context, session: WorkoutSession): Boolean {
        val ok = try {
            HistoryDb.get(context).dao().upsert(WorkoutMapper.map(session))
            true
        } catch (t: Throwable) {
            Timber.w(t, "Saving the workout failed")
            AppLog.failure(LogSource.Health, "Saving the workout failed", t)
            false
        }
        if (ok) {
            if (last == null || (session.startUtcMillis ?: 0L) >= (last?.startUtcMillis ?: 0L)) last = session
            runCatching { onDownloaded?.invoke() }
        }
        return ok
    }
}
