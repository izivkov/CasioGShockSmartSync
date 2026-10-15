package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.AppInfoIO
import org.avmedia.gshockapi.io.ButtonPressedIO
import org.avmedia.gshockapi.io.DstForWorldCitiesIO
import org.avmedia.gshockapi.io.DstWatchStateIO
import org.avmedia.gshockapi.io.ErrorIO
import org.avmedia.gshockapi.io.EventsIO
import org.avmedia.gshockapi.io.GwBx5600TimeIO
import org.avmedia.gshockapi.io.HomeTimeIO
import org.avmedia.gshockapi.io.RunActionsIO
import org.avmedia.gshockapi.io.SettingsIO
import org.avmedia.gshockapi.io.StepCounterIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.io.UnknownIO
import org.avmedia.gshockapi.io.WatchConditionIO
import org.avmedia.gshockapi.io.WatchNameIO
import org.avmedia.gshockapi.io.WorldCitiesIO

@RequiresApi(Build.VERSION_CODES.O)
interface WatchProtocol {
    val dataReceivedHandlers: Map<Int, (String) -> Unit>
        get() = mapOf(
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
            CasioConstants.CHARACTERISTICS.CASIO_ACTIVITY_RECORD.code to StepCounterIO::onReceived,
            CasioConstants.CHARACTERISTICS.ERROR.code to ErrorIO::onReceived,
            CasioConstants.CHARACTERISTICS.FIND_PHONE.code to RunActionsIO::onReceived,
            CasioConstants.CHARACTERISTICS.CMD_SET_TIMEMODE.code to UnknownIO::onReceived,
            CasioConstants.CHARACTERISTICS.CASIO_HOME_TIME.code to HomeTimeIO::onReceived,
            CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_03.code to GwBx5600TimeIO::onReceivedStep2,
            CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_05.code to GwBx5600TimeIO::onReceivedStep1,
            CasioConstants.CHARACTERISTICS.GW_BX5600_SP_DATA_HEADER_06.code to GwBx5600TimeIO::onReceivedStep3,
        )

    fun extractKey(data: String): Int?
    fun unwrapPayload(data: String, key: Int): String
    fun getWatchConditionRequest(): String
    suspend fun setTime(timeMs: Long?, offset: Long?)
    suspend fun getTimer(): Int
    fun setTimer(timerValue: Int)
    fun getTimerRequest(): String
    fun getTimerSize(): Int
    suspend fun getHomeTime(): String
    suspend fun getBatteryLevel(): Int
    suspend fun getWatchTemperature(): Int
    suspend fun getAlarms(): ArrayList<Alarm>
    fun setAlarms(alarms: ArrayList<Alarm>)
    suspend fun getSettings(): Settings
    fun setSettings(settings: Settings)
    suspend fun getBasicSettings(): Settings
    suspend fun getTimeAdjustment(): TimeAdjustmentInfo
}
