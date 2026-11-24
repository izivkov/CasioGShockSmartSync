package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.smartsync.plugin.api.Capability
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

/** The kind of data of a value key, as the host's registry states it; null: not served to any plugin. */
typealias ValueUses = (key: String) -> DataUse?

/**
 * The host keeps each plugin to its [PluginAccess]: what it did not declare it does not get or do. A value key whose
 * kind the registry does not state is never served (fail closed). Android-free.
 */
object AccessGuard {
    fun mayRead(access: PluginAccess, uses: ValueUses, key: String): Boolean = uses(key)?.let { it in access.reads } ?: true

    /** [values] with only the declared kinds. */
    fun values(access: PluginAccess, uses: ValueUses, values: WatchValues): WatchValues = WatchValues(
        values.watch,
        values.values.filterKeys { mayRead(access, uses, it) },
        values.readAtMillis.filterKeys { mayRead(access, uses, it) },
    )

    fun mayRead(access: PluginAccess, kind: DataKind<*>): Boolean = DataUse.of(kind) in access.reads

    /** The kinds of data that are health data: [org.avmedia.smartsync.plugin.api.PluginNeeds.healthReads] needs one. */
    val HEALTH: Set<DataUse> = setOf(DataUse.STEPS, DataUse.HEART_RATE, DataUse.SLEEP, DataUse.BLOOD_OXYGEN, DataUse.WORKOUTS)

    fun readsHealth(access: PluginAccess): Boolean = access.reads.any { it in HEALTH }

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

    /** The controls [command] needs (a key the registry does not know needs nothing here: it is refused as unknown). */
    fun controlsOf(command: WatchCommand): Set<Control> = when (command) {
        WatchCommand.RefreshValues -> emptySet()
        is WatchCommand.ChangeSettings -> command.values.keys.filter { WatchSettingRegistry.find(it) != null }.map(WatchSettingRegistry::controlOf).toSet()
    }

    fun mayRun(access: PluginAccess, command: WatchCommand): Boolean = access.controls.containsAll(controlsOf(command))

    /**
     * [event] as this plugin may see it, or null when it may not see it at all. Exhaustive: a new event type needs its
     * rule here before it compiles.
     */
    fun event(access: PluginAccess, uses: ValueUses, event: WatchEvent): WatchEvent? = when (event) {
        is WatchEvent.ValuesUpdated -> {
            val v = values(access, uses, event.values)
            val now = event.readNow.filter { mayRead(access, uses, it) }.toSet()
            if (v.values.isEmpty() && now.isEmpty()) null else event.copy(values = v, readNow = now)
        }
        is WatchEvent.HealthRead -> event.kinds.filter { mayRead(access, it) }.toSet().takeIf { it.isNotEmpty() }?.let { event.copy(kinds = it) }
        is WatchEvent.WorkoutStored -> event.takeIf { DataUse.WORKOUTS in access.reads }
        is WatchEvent.ButtonPressed -> event.takeIf { Control.BUTTON_PRESS in access.controls }
        is WatchEvent.SettingChanged -> event.takeIf { true } ?: event.takeIf { uses(org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables.valueKey(event.key))?.let { u -> u in access.reads } == true }
        is WatchEvent.Connected, is WatchEvent.Disconnected, is WatchEvent.WatchRemoved, is WatchEvent.WatchRePaired -> event
        WatchEvent.BackgroundStarted, WatchEvent.BackgroundStopped, WatchEvent.EventsLost, WatchEvent.Wake, is WatchEvent.PermissionsChanged -> event
    }

    /** The first interface version that has [event]: a plugin built against an older one never gets it. */
    fun since(event: WatchEvent): String = when (event) {
        is WatchEvent.Connected, is WatchEvent.Disconnected, is WatchEvent.ValuesUpdated, is WatchEvent.HealthRead,
        is WatchEvent.WorkoutStored, is WatchEvent.ButtonPressed, is WatchEvent.SettingChanged, is WatchEvent.WatchRemoved,
        is WatchEvent.WatchRePaired, WatchEvent.BackgroundStarted, WatchEvent.BackgroundStopped, WatchEvent.EventsLost,
        WatchEvent.Wake, is WatchEvent.PermissionsChanged -> "0.1.0"
    }

    /** How the app names a kind of data (UI text: the host's, not the interface's). */
    fun words(use: DataUse): String = when (use) {
        DataUse.STEPS -> "steps, distance and energy"
        DataUse.HEART_RATE -> "heart rate"
        DataUse.SLEEP -> "sleep"
        DataUse.BLOOD_OXYGEN -> "blood oxygen"
        DataUse.WORKOUTS -> "workouts"
        DataUse.BATTERY_AND_TEMPERATURE -> "watch battery and temperature"
        DataUse.WATCH_SETTINGS -> "watch settings, alarms and cities"
        DataUse.BODY_PROFILE -> "body profile (height, weight, heart rate limits)"
        DataUse.WATCH_INFO -> "watch name, model and firmware"
        DataUse.PHONE -> "phone time zone and app state"
        DataUse.PHONE_BATTERY -> "phone battery"
        DataUse.PHONE_LOCATION -> "phone location (coarse)"
    }

    fun words(control: Control): String = when (control) {
        Control.NOTIFICATIONS -> "show notifications on the watch"
        Control.WATCH_SETTINGS -> "change watch settings"
        Control.WATCH_FACE -> "change the watch face"
        Control.BUTTON_PRESS -> "run on a press of the watch button"
    }

    /** "Reads: steps, heart rate. Controls: notifications. Sends to: your server. Keeps: the token." in lines, for the details. */
    fun lines(access: PluginAccess): List<Pair<String, String>> = listOfNotNull(
        "Reads" to access.reads.sortedBy { it.ordinal }.joinToString { words(it) }.ifEmpty { "nothing" },
        "Controls" to access.controls.sortedBy { it.ordinal }.joinToString { words(it) }.ifEmpty { "nothing" },
        "Sends to" to access.sendsTo.joinToString().ifEmpty { "nowhere" },
        "Keeps" to access.stores.joinToString().ifEmpty { "nothing" },
        PluginPermissions.required(access).takeIf { it.isNotEmpty() }?.let { p -> "Android permissions" to p.joinToString("\n") { "${it.reason} (${it.name.substringAfterLast('.')})" } },
    )

    /** "GBX-H5600 (all features)", "OCW-S7000 (without health log, steps)", "X (not supported: needs health log)". */
    fun worksWith(watches: List<Pair<String, WatchSupport>>): List<String> = watches.map { (name, s) ->
        fun words(c: Set<Capability>) = c.sortedBy { it.ordinal }.joinToString { it.name.lowercase().replace('_', ' ') }
        when (s) {
            WatchSupport.Full -> "$name (all features)"
            is WatchSupport.Partial -> "$name (without ${words(s.missing)})"
            is WatchSupport.NotSupported -> "$name (not supported: needs ${words(s.missing)})"
        }
    }
}
