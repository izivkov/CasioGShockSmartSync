package org.avmedia.gshockGoogleSync.history

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

    /** Called after each stored LifeLog transfer. */
    fun record(context: Context, data: StepCounterData) {
        scope.launch {
            try {
                HistoryDb.get(context).dao().upsert(HistoryMapper.map(data))
            } catch (t: Throwable) {
                Timber.w(t, "Saving the health history failed")
            }
        }
    }
}
