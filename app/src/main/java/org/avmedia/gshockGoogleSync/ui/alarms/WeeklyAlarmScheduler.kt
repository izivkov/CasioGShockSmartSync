package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyAlarmScheduler @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val context: Context
) {
    init {
        ProgressEvents.runEventActions(
            Utils.AppHashCode() + "WeeklyAlarmScheduler",
            arrayOf(
                EventAction("WatchInitializationCompleted") {
                    CoroutineScope(Dispatchers.IO).launch { applyTodaySchedule() }
                }
            )
        )
    }

    fun ensureInitialized() = Unit

    suspend fun applyTodaySchedule() {
        val viewMode = AlarmSyncStorage.loadViewMode(context)
        val alarmDays = AlarmSyncStorage.loadDaySelections(context)
        val storedAlarms = AlarmSyncStorage.loadAlarms(context)
        val shouldWrite = AlarmSyncStorage.isDirty(context) || viewMode == AlarmViewMode.WEEKLY

        if (!shouldWrite) return

        val currentAlarms = runCatching { api.getAlarms() }.getOrElse {
            Timber.e(it, "Failed to get alarms before applying local schedule")
            return
        }

        val desiredAlarms = storedAlarms?.takeIf { it.isNotEmpty() } ?: currentAlarms
        val newAlarms = AlarmSchedulePlanner.applyWeeklySchedule(
            alarms = desiredAlarms,
            alarmDays = alarmDays,
            now = LocalDateTime.now(),
            viewMode = viewMode
        )

        if (newAlarms != currentAlarms) {
            runCatching { api.setAlarms(ArrayList(newAlarms)) }.onFailure {
                Timber.e(it, "Failed to apply per-alarm day schedule")
            }.onSuccess {
                AlarmSyncStorage.saveAlarms(context, desiredAlarms, dirty = false)
            }
        } else if (AlarmSyncStorage.isDirty(context)) {
            AlarmSyncStorage.saveAlarms(context, desiredAlarms, dirty = false)
        }
    }
}
