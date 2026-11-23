package org.avmedia.gshockGoogleSync.history

import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogSource
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.model.StepCounterData
import timber.log.Timber

/** Keeps the history of the decoded transfers in [HistoryDb]. A failure is logged, never thrown into the watch session. */
object HistoryStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [data] laid over the stored hours of its day ([TodayHours]); blocking, call off the main thread. */
    fun mergeDay(context: Context, data: StepCounterData): StepCounterData {
        val day = data.timestamp?.toLocalDate() ?: return data
        return TodayHours.merge(data, HistoryDb.get(context).dao().hoursOf(day.toString()))
    }

    /** Called after each stored LifeLog transfer. */
    fun record(context: Context, data: StepCounterData) {
        LastReadStore.save(context, data)
        scope.launch {
            try {
                HistoryDb.get(context).dao().upsert(HistoryMapper.map(data))
            } catch (t: Throwable) {
                Timber.w(t, "Saving the health history failed")
                AppLog.failure(LogSource.Health, "Saving the health history failed", t)
            }
        }
    }
}
