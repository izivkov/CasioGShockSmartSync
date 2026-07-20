/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 10:02 a.m.
 */

package org.avmedia.gshockapi.casio

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.AppInfoIO
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.DstForWorldCitiesIO
import org.avmedia.gshockapi.io.DstWatchStateIO
import org.avmedia.gshockapi.io.ErrorIO
import org.avmedia.gshockapi.io.EventsIO
import org.avmedia.gshockapi.io.RunActionsIO
import org.avmedia.gshockapi.io.SettingsIO
import org.avmedia.gshockapi.io.StepCounterIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.io.UnknownIO
import org.avmedia.gshockapi.io.WatchConditionIO
import org.avmedia.gshockapi.io.WatchNameIO
import org.avmedia.gshockapi.io.WorldCitiesIO
import org.avmedia.gshockapi.io.GwBx5600TimeIO
import org.avmedia.gshockapi.utils.Utils
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
        "GET_ALARMS"          to AlarmsIO::sendToWatch,
        "SET_ALARMS"          to AlarmsIO::sendToWatchSet,
        "SET_REMINDERS"       to EventsIO::sendToWatchSet,
        "GET_SETTINGS"        to SettingsIO::sendToWatch,
        "SET_SETTINGS"        to SettingsIO::sendToWatchSet,
        "GET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatch,
        "SET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatchSet,
        "GET_TIMER"           to TimerIO::sendToWatch,
        "SET_TIMER"           to TimerIO::sendToWatchSet,
        "SET_TIME"            to TimeIO::sendToWatchSet,
    )

    /**
     * Maps inbound characteristic codes to their handler functions.
     * Pure data — no side effects, no mutable state.
     */
    private val dataReceivedHandlers: Map<Int, (String) -> Unit> = mapOf(
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code   to AlarmsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code  to AlarmsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code       to DstForWorldCitiesIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code     to EventsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code    to EventsIO::onReceivedTitle,
        CasioConstants.CHARACTERISTICS.CASIO_TIMER.code             to TimerIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code      to WorldCitiesIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code   to DstWatchStateIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code        to WatchNameIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code   to WatchConditionIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_APP_INFORMATION.code   to AppInfoIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code      to ButtonPressedIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code to SettingsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code   to TimeAdjustmentIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_ACTIVITY_RECORD.code   to StepCounterIO::onReceived,
        CasioConstants.CHARACTERISTICS.ERROR.code                   to ErrorIO::onReceived,
        CasioConstants.CHARACTERISTICS.FIND_PHONE.code              to RunActionsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CMD_SET_TIMEMODE.code        to UnknownIO::onReceived,
        CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_03.code to GwBx5600TimeIO::onReceived,
        CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_05.code to GwBx5600TimeIO::onReceived,
        CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_06.code to GwBx5600TimeIO::onReceived,
    )

    // =========================================================================
    // Pure helpers
    // =========================================================================

    /** Pure: extract the action string from an outbound message. */
    private fun extractAction(message: String): String? =
        runCatching { JSONObject(message).getString("action") }
            .onFailure { Timber.e("Failed to parse action from message: $message") }
            .getOrNull()

    /** Pure: extract the characteristic key from inbound data. */
    private fun extractKey(data: String): Int? =
        runCatching { Utils.toIntArray(data)[0] }
            .onFailure { Timber.e("Failed to extract key from data: $data") }
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

    fun onReceived(data: String) {
        val key = extractKey(data) ?: return
        val handler = dataReceivedHandlers[key]
        if (handler == null) {
            Timber.e("No handler registered for key: $key")
            return
        }
        handler(data)
    }
}
