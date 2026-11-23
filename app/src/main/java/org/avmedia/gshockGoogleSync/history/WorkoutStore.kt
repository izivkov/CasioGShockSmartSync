package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource
import android.content.Context
import org.avmedia.gshockapi.model.WorkoutSession
import timber.log.Timber

/**
 * Keeps downloaded workouts in [HistoryDb]; the Home Assistant sensors read the newest stored one ([newest]).
 * The watch is told "fetched" only when [save] returned true, so a failed save is read again next time.
 */
object WorkoutStore {
    /** The newest stored workout (also one read in an earlier app session); null when none is stored or the database fails. */
    suspend fun newest(context: Context): WorkoutRow? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { HistoryDb.get(context).dao().newestWorkout() }
            .onFailure { Timber.w(it, "Reading the newest workout failed") }.getOrNull()
    }

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
        if (ok) runCatching { onDownloaded?.invoke() }
        return ok
    }
}
