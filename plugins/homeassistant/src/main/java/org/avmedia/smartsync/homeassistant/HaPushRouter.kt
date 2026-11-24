package org.avmedia.smartsync.homeassistant

import java.time.LocalDateTime
import org.avmedia.smartsync.plugin.api.WatchNotification
import timber.log.Timber

/**
 * Decides what a Home Assistant push becomes. A normal message goes to the watch; a companion-app command never does:
 * `command_update_sensors` asks for one sensor push, `command_watch_setting` changes watch settings, every other command is logged and ignored.
 */
class HaPushRouter(
    private val deliver: (WatchNotification) -> Unit,
    private val updateSensors: () -> Unit = {},
    private val log: (String) -> Unit = { Timber.tag(AppLog.TAG).i(it) },
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val watchSetting: (Map<String, Any?>) -> Unit = {},
) {
    fun route(push: HaPushEvent.Push) {
        val command = push.command
        when {
            command == null -> deliver(HaPush.toNotification(push, now()))
            command == HaPush.COMMAND_UPDATE_SENSORS -> {
                log("HA push: $command, pushing the sensors")
                updateSensors()
            }
            command == HaPush.COMMAND_WATCH_SETTING -> watchSetting(push.data)
            else -> log("HA push: command $command ignored")
        }
    }
}
