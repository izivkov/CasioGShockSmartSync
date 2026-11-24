package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingCommands
import org.avmedia.gshockapi.AppNotification
import timber.log.Timber

/**
 * Decides what a Home Assistant push becomes. A normal message goes to the watch; a companion-app command never does:
 * `command_update_sensors` asks for one sensor push, `command_watch_setting` changes watch settings, every other command is logged and ignored.
 */
class HaPushRouter(
    private val deliver: (AppNotification) -> Unit,
    private val updateSensors: () -> Unit = { updateSensorsHook?.invoke() },
    private val log: (String) -> Unit = { Timber.i(it) },
    private val now: () -> String,
    private val watchSetting: (Map<String, Any?>) -> Unit = { WatchSettingCommands.shared?.submit(it) },
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

    companion object {
        /** Set by [HaSync]: queues one Home Assistant sensor push. */
        @Volatile var updateSensorsHook: (() -> Unit)? = null
    }
}
