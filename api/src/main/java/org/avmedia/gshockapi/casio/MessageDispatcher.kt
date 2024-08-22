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
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.io.UnknownIO
import org.avmedia.gshockapi.io.WatchConditionIO
import org.avmedia.gshockapi.io.WatchNameIO
import org.avmedia.gshockapi.io.WorldCitiesIO
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber

object MessageDispatcher {

    @RequiresApi(Build.VERSION_CODES.O)
    private val watchSenders = mapOf<String, (String) -> Unit>(
        "GET_ALARMS" to AlarmsIO::sendToWatch,
        "SET_ALARMS" to AlarmsIO::sendToWatchSet,
        "SET_REMINDERS" to EventsIO::sendToWatchSet,
        "GET_SETTINGS" to SettingsIO::sendToWatch,
        "SET_SETTINGS" to SettingsIO::sendToWatchSet,
        "GET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatch,
        "SET_TIME_ADJUSTMENT" to TimeAdjustmentIO::sendToWatchSet,
        "GET_TIMER" to TimerIO::sendToWatch,
        "SET_TIMER" to TimerIO::sendToWatchSet,
        "SET_TIME" to TimeIO::sendToWatchSet,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendToWatch(message: String) {
        val action = JSONObject(message).get("action")
        watchSenders[action]!!.invoke(message)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val dataReceivedMessages = mapOf<Int, (String) -> Unit>(
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM.code to AlarmsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_ALM2.code to AlarmsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_DST_SETTING.code to DstForWorldCitiesIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code to EventsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code to EventsIO::onReceivedTitle,
        CasioConstants.CHARACTERISTICS.CASIO_TIMER.code to TimerIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WORLD_CITIES.code to WorldCitiesIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_DST_WATCH_STATE.code to DstWatchStateIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_NAME.code to WatchNameIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_WATCH_CONDITION.code to WatchConditionIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_APP_INFORMATION.code to AppInfoIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_BLE_FEATURES.code to ButtonPressedIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BASIC.code to SettingsIO::onReceived,
        CasioConstants.CHARACTERISTICS.CASIO_SETTING_FOR_BLE.code to TimeAdjustmentIO::onReceived,

        CasioConstants.CHARACTERISTICS.ERROR.code to ErrorIO::onReceived,
        CasioConstants.CHARACTERISTICS.FIND_PHONE.code to RunActionsIO::onReceived, // always-connected watches, use PHONE FINDER to invoke actions...
        CasioConstants.CHARACTERISTICS.CMD_SET_TIMEMODE.code to UnknownIO::onReceived,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun onReceived(data: String) {
        val intArray = Utils.toIntArray(data)
        val key = intArray[0]
        if (dataReceivedMessages[key] == null) {
            Timber.e("GShockAPI", "Unknown key: $key")
        }
        dataReceivedMessages[key]?.invoke(data)
    }
}
