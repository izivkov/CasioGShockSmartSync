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
     * Changes settings, key to value ([PluginHost.settingCatalog]). Each value is checked first; a refused one is
     * logged and the others are written. Values: a choice as its word, a number as Int, on/off as Boolean, a time as
     * "HH:MM". After the writes the host reads the values again ([WatchEvent.ValuesUpdated], cause REQUEST).
     */
    data class ChangeSettings(val values: Map<String, Any?>) : WatchCommand

    /** Read the values now ([PluginNeeds.values]); the host sends [WatchEvent.ValuesUpdated] (cause REQUEST). */
    data object RefreshValues : WatchCommand
}

/** One setting [WatchCommand.ChangeSettings] accepts. */
data class SettingInfo(
    val key: String,
    val type: SettingType,
    /** For [SettingType.CHOICE]: the words allowed for this watch. */
    val choices: List<String> = emptyList(),
    /** For [SettingType.WHOLE]: the range, and when set, the only values allowed in it. */
    val range: IntRange? = null,
    val only: Set<Int>? = null,
)

enum class SettingType { CHOICE, WHOLE, BOOL, TIME }

/** The decoded settings of a watch, key to value, as read at [readAtMillis] (null = never). */
data class SettingsSnapshot(val watch: WatchId, val values: Map<String, Any?>, val readAtMillis: Long?)
