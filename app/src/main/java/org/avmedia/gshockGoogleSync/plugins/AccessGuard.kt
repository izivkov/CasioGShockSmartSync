package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.DayRecord
import org.avmedia.smartsync.plugin.api.HistoryRecord
import org.avmedia.smartsync.plugin.api.HourRecord
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchValues

/** The host keeps each plugin to its [PluginAccess]: what it did not declare it does not get or do. Android-free. */
object AccessGuard {
    fun mayRead(access: PluginAccess, key: String): Boolean = DataUse.ofValueKey(key) in access.reads

    /** [values] with only the declared kinds. */
    fun values(access: PluginAccess, values: WatchValues): WatchValues = WatchValues(
        values.watch,
        values.values.filterKeys { mayRead(access, it) },
        values.readAtMillis.filterKeys { mayRead(access, it) },
    )

    fun mayRead(access: PluginAccess, kind: DataKind<*>): Boolean = DataUse.of(kind) in access.reads

    /** A record with the fields of undeclared kinds left out (a day also holds heart rate and blood oxygen). */
    @Suppress("UNCHECKED_CAST")
    fun <T : HistoryRecord> record(access: PluginAccess, r: T): T {
        val hr = DataUse.HEART_RATE in access.reads
        val o2 = DataUse.BLOOD_OXYGEN in access.reads
        return when (r) {
            is DayRecord -> r.copy(
                hrRest = r.hrRest.takeIf { hr }, hrMin = r.hrMin.takeIf { hr }, hrMax = r.hrMax.takeIf { hr }, spo2 = r.spo2.takeIf { o2 },
            ) as T
            is HourRecord -> r.copy(heartRate = r.heartRate.takeIf { hr }) as T
            else -> r
        }
    }

    /** The controls [command] needs. */
    fun controlsOf(command: WatchCommand): Set<Control> = when (command) {
        WatchCommand.RefreshValues -> emptySet()
        is WatchCommand.ChangeSettings -> command.values.keys.map(Control::ofSetting).toSet()
    }

    fun mayRun(access: PluginAccess, command: WatchCommand): Boolean = access.controls.containsAll(controlsOf(command))

    /** [event] as this plugin may see it, or null when it may not see it at all. */
    fun event(access: PluginAccess, event: WatchEvent): WatchEvent? = when (event) {
        is WatchEvent.ValuesUpdated -> {
            val v = values(access, event.values)
            val now = event.readNow.filter { mayRead(access, it) }.toSet()
            if (v.values.isEmpty() && now.isEmpty()) null else event.copy(values = v, readNow = now)
        }
        is WatchEvent.HealthRead -> event.kinds.filter { mayRead(access, it) }.toSet().takeIf { it.isNotEmpty() }?.let { event.copy(kinds = it) }
        is WatchEvent.WorkoutStored -> event.takeIf { DataUse.WORKOUTS in access.reads }
        is WatchEvent.ButtonPressed -> event.takeIf { Control.BUTTON_PRESS in access.controls }
        is WatchEvent.SettingChanged -> event.takeIf { DataUse.WATCH_SETTINGS in access.reads }
        is WatchEvent.Condition -> event.takeIf { DataUse.BATTERY_AND_TEMPERATURE in access.reads }
        else -> event
    }

    /** "Reads: steps, heart rate. Controls: notifications. Sends to: your server. Keeps: the token." in lines, for the details sheet. */
    fun lines(access: PluginAccess): List<Pair<String, String>> = listOf(
        "Reads" to access.reads.sortedBy { it.ordinal }.joinToString { it.words }.ifEmpty { "nothing" },
        "Controls" to access.controls.sortedBy { it.ordinal }.joinToString { it.words }.ifEmpty { "nothing" },
        "Sends to" to access.sendsTo.joinToString().ifEmpty { "nowhere" },
        "Keeps" to access.stores.joinToString().ifEmpty { "nothing" },
    )

    /** "GBX-H5600 (all features)", "OCW-S7000 (without health log, steps)", "X (not supported: needs health log)". */
    fun worksWith(watches: List<Pair<String, org.avmedia.smartsync.plugin.api.WatchSupport>>): List<String> = watches.map { (name, s) ->
        fun words(c: Set<org.avmedia.smartsync.plugin.api.Capability>) = c.sortedBy { it.ordinal }.joinToString { it.name.lowercase().replace('_', ' ') }
        when (s) {
            org.avmedia.smartsync.plugin.api.WatchSupport.Full -> "$name (all features)"
            is org.avmedia.smartsync.plugin.api.WatchSupport.Partial -> "$name (without ${words(s.missing)})"
            is org.avmedia.smartsync.plugin.api.WatchSupport.NotSupported -> "$name (not supported: needs ${words(s.missing)})"
        }
    }
}
