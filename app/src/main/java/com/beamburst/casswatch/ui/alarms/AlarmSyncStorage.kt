package com.beamburst.casswatch.ui.alarms

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.beamburst.casswatch.utils.LocalDataStorage
import org.avmedia.gshockapi.Alarm
import java.time.DayOfWeek

object AlarmSyncStorage {
    private const val ALARMS_KEY = "PhoneAlarmDrafts"
    private const val ALARMS_DIRTY_KEY = "PhoneAlarmDraftsDirty"
    private const val ALARM_VIEW_MODE_KEY = "AlarmViewMode"
    private const val ALARM_DAYS_KEY = "AlarmDaySelections"

    private val gson = Gson()

    data class StoredAlarm(
        val hour: Int,
        val minute: Int,
        val enabled: Boolean,
        val hasHourlyChime: Boolean,
        val name: String?
    )

    fun saveAlarms(context: Context, alarms: List<Alarm>, dirty: Boolean) {
        val stored = alarms.map {
            StoredAlarm(
                hour = it.hour,
                minute = it.minute,
                enabled = it.enabled,
                hasHourlyChime = it.hasHourlyChime,
                name = it.name
            )
        }
        LocalDataStorage.put(context, ALARMS_KEY, gson.toJson(stored))
        LocalDataStorage.put(context, ALARMS_DIRTY_KEY, dirty.toString())
    }

    fun loadAlarms(context: Context): List<Alarm>? {
        val json = LocalDataStorage.get(context, ALARMS_KEY) ?: return null
        if (json.isBlank()) return null

        val type = object : TypeToken<List<StoredAlarm>>() {}.type
        val stored = runCatching {
            gson.fromJson<List<StoredAlarm>>(json, type)
        }.getOrNull() ?: return null

        return stored.map {
            Alarm(
                it.hour,
                it.minute,
                it.enabled,
                it.hasHourlyChime,
                it.name
            )
        }
    }

    fun isDirty(context: Context): Boolean =
        LocalDataStorage.get(context, ALARMS_DIRTY_KEY, "false")?.toBoolean() ?: false

    fun saveViewMode(context: Context, mode: AlarmViewMode) {
        LocalDataStorage.put(context, ALARM_VIEW_MODE_KEY, mode.name)
    }

    fun loadViewMode(context: Context): AlarmViewMode {
        val stored = LocalDataStorage.get(context, ALARM_VIEW_MODE_KEY)
        return if (stored == AlarmViewMode.WEEKLY.name) AlarmViewMode.WEEKLY else AlarmViewMode.SIMPLE
    }

    fun saveDaySelections(context: Context, alarmDays: Map<Int, Set<DayOfWeek>>) {
        val toStore = alarmDays
            .mapKeys { it.key.toString() }
            .mapValues { it.value.map { day -> day.name } }
        LocalDataStorage.put(context, ALARM_DAYS_KEY, gson.toJson(toStore))
    }

    fun loadDaySelections(context: Context): Map<Int, Set<DayOfWeek>> {
        val json = LocalDataStorage.get(context, ALARM_DAYS_KEY) ?: return emptyMap()
        if (json.isBlank()) return emptyMap()

        val type = object : TypeToken<Map<String, List<String>>>() {}.type
        val raw = runCatching {
            gson.fromJson<Map<String, List<String>>>(json, type)
        }.getOrNull() ?: return emptyMap()

        return raw.mapNotNull { (key, values) ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            val days = values.mapNotNull {
                runCatching { DayOfWeek.valueOf(it) }.getOrNull()
            }.toSet()
            index to days
        }.toMap()
    }
}
