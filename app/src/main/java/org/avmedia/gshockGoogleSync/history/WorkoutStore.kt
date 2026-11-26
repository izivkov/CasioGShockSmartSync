package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogSource
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

    /** Called after each workout stored for the first time (starts the Home Assistant push); a repeat does not call it. */
    @Volatile var onDownloaded: (() -> Unit)? = null

    /** Stores [session]; true when it is in the database (also when it was there already). */
    suspend fun save(context: Context, session: WorkoutSession): Boolean {
        val isNew = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HistoryDb.get(context).dao().upsert(WorkoutMapper.map(session)) }
        } catch (t: Throwable) {
            Timber.w(t, "Saving the workout failed")
            AppLog.failure(LogSource.Workouts, "Saving the workout failed", t)
            return false
        }
        if (isNew) runCatching { onDownloaded?.invoke() }
        return true
    }
}
