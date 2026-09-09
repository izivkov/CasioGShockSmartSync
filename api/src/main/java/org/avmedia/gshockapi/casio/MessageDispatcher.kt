/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.EventsIO
import org.avmedia.gshockapi.io.SettingsIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.protocols.StandardProtocol
import org.avmedia.gshockapi.protocols.WatchProtocol
import org.json.JSONObject
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
object MessageDispatcher {

    // =========================================================================
    // Pure Functional Core: dispatch tables
    // =========================================================================

    /**
     * Maps outbound action strings to their handler functions.
     * Pure data — no side effects, no mutable state.
     */
    private val watchSenders: Map<String, (String) -> Unit> = mapOf(
        "GET_ALARMS"          to { AlarmsIO.sendToWatch(it) },
        "SET_ALARMS"          to { AlarmsIO.sendToWatchSet(it) },
        "SET_REMINDERS"       to { EventsIO.sendToWatchSet(it) },
        "GET_SETTINGS"        to { SettingsIO.sendToWatch(it) },
        "SET_SETTINGS"        to { SettingsIO.sendToWatchSet(it) },
        "GET_TIME_ADJUSTMENT" to { TimeAdjustmentIO.sendToWatch(it) },
        "SET_TIME_ADJUSTMENT" to { TimeAdjustmentIO.sendToWatchSet(it) },
        "GET_TIMER"           to { TimerIO.sendToWatch(it) },
        "SET_TIMER"           to { TimerIO.sendToWatchSet(it) },
        "SET_TIME"            to { TimeIO.sendToWatchSet(it) },
    )

    // =========================================================================
    // Pure helpers
    // =========================================================================

    /** Pure: extract the action string from an outbound message. */
    private fun extractAction(message: String): String? =
        runCatching { JSONObject(message).getString("action") }
            .onFailure { Timber.e("Failed to parse action from message: $message") }
            .getOrNull()

    // =========================================================================
    // Imperative Shell: dispatch with logging
    // =========================================================================

    fun sendToWatch(message: String) {
        val action = extractAction(message) ?: return
        val handler = watchSenders[action]
        if (handler == null) {
            Timber.e("No sender registered for action: $action")
            return
        }
        handler(message)
    }

    fun onReceived(data: String, protocol: WatchProtocol = StandardProtocol) {
        val key = protocol.extractKey(data) ?: return
        Timber.d("MessageDispatcher: onReceived key: $key, data: $data")
        val handler = protocol.dataReceivedHandlers[key]
        if (handler == null) {
            Timber.e("No handler registered for key: $key")
            return
        }

        val dataToProcess = protocol.unwrapPayload(data, key)
        handler(dataToProcess)
    }
}
