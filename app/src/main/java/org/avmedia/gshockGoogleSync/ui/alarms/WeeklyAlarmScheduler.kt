package org.avmedia.gshockGoogleSync.ui.alarms

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyAlarmScheduler @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val context: Context
) {
    init {
        ProgressEvents.runEventActions(Utils.AppHashCode(), arrayOf(
            EventAction("WatchInitializationCompleted") {
                CoroutineScope(Dispatchers.IO).launch { applyTodaySchedule() }
            }
        ))
    }

    suspend fun applyTodaySchedule() {
        val json = LocalDataStorage.get(context, AlarmViewModel.ALARM_DAYS_KEY) ?: return
        if (json.isBlank()) return

        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val rawDays = runCatching {
            Gson().fromJson<Map<String, List<String>>>(json, type)
        }.getOrNull() ?: return

        if (rawDays.isEmpty()) return

        val alarmDays = rawDays.mapNotNull { (k, v) ->
            val index = k.toIntOrNull() ?: return@mapNotNull null
            val days = v.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
            index to days
        }.toMap()

        val today = LocalDate.now().dayOfWeek
        val currentAlarms = runCatching { api.getAlarms() }.getOrElse {
            Timber.e(it, "Failed to get alarms for weekly schedule")
            return
        }

        val newAlarms = currentAlarms.mapIndexed { index, alarm ->
            val selected = alarmDays[index]
            if (!selected.isNullOrEmpty() && today !in selected)
                alarm.copy(enabled = false)
            else
                alarm
        }

        if (newAlarms != currentAlarms) {
            runCatching { api.setAlarms(ArrayList(newAlarms)) }.onFailure {
                Timber.e(it, "Failed to apply per-alarm day schedule")
            }
        }
    }
}
