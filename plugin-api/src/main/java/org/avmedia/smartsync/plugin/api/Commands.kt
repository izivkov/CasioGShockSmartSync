package org.avmedia.smartsync.plugin.api

import java.time.LocalDateTime

/** A notification for the watch's notification list. The watch may cut long fields. */
data class WatchNotification(
    val kind: NotificationKind,
    /** The source line (an app name); some watches show about 10 characters. */
    val app: String,
    val title: String,
    val text: String,
    /** Phone local time. */
    val time: LocalDateTime = LocalDateTime.now(),
)

/** The icon the watch shows. */
enum class NotificationKind { MESSAGE, CALENDAR, EMAIL, CALL, GENERIC }

/** Something a plugin asks the watch to do. */
sealed interface WatchCommand {
    /**
     * Changes settings: [values] maps a [SettingInfo.key] (the same key as in [SettingsSnapshot.values] and
     * [WatchEvent.SettingChanged.key]) to its new value. Values: a choice as its word, a number as a whole number, on/off
     * as Boolean, a time as "HH:MM".
     *
     * All or nothing: every key and value is checked first. An unknown key gives `Failed(NOT_SUPPORTED, "<key>: ...")`,
     * a bad value `Failed(INVALID, "<key>: <reason>")`, and then nothing is written. A write that fails names its key in
     * the message. After the writes the host reads the values again ([WatchEvent.ValuesUpdated], cause REQUEST).
     */
    data class ChangeSettings(val values: Map<String, Any?>) : WatchCommand

    /**
     * Read the values now ([PluginNeeds.values]); the host sends [WatchEvent.ValuesUpdated] (cause REQUEST). Needs at
     * least one declared [DataUse]. A read already queued is joined, not repeated. With [Delivery.NextConnection] and
     * no connection the result is `Ok(AT_NEXT_CONNECTION)`: every connection reads the values anyway.
     */
    data object RefreshValues : WatchCommand
}

/** One setting [WatchCommand.ChangeSettings] accepts. */
data class SettingInfo(
    /** The one key of this setting: in [WatchCommand.ChangeSettings], [SettingsSnapshot.values] and [WatchEvent.SettingChanged]. */
    val key: String,
    val type: SettingType,
    /** For [SettingType.CHOICE]: the words allowed for this watch. */
    val choices: List<String> = emptyList(),
    /** For [SettingType.WHOLE]: the range, and when set, the only values allowed in it. */
    val range: IntRange? = null,
    val only: Set<Int>? = null,
    /** A short name for the person ("Step goal"). */
    val label: String = key,
    /** What writing it needs ([PluginAccess.controls]). */
    val control: Control = Control.WATCH_SETTINGS,
)

enum class SettingType { CHOICE, WHOLE, BOOL, TIME }

/**
 * The decoded settings of a watch as read last, by [SettingInfo.key] (one entry per catalogue row that was read; the
 * same key everywhere), read at [readAtMillis] (null = never).
 */
data class SettingsSnapshot(val watch: WatchId, val values: Map<String, Any?>, val readAtMillis: Long?)
