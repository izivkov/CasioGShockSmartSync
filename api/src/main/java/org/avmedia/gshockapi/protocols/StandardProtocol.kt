package org.avmedia.gshockapi.protocols

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.io.AlarmsIO
import org.avmedia.gshockapi.io.HomeTimeIOFunctional
import org.avmedia.gshockapi.io.SettingsIO
import org.avmedia.gshockapi.io.TimeAdjustmentIO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import org.avmedia.gshockapi.io.TimeIO
import org.avmedia.gshockapi.io.TimerIO
import org.avmedia.gshockapi.io.WatchConditionIO
import org.avmedia.gshockapi.io.WorldCitiesIO
import org.avmedia.gshockapi.utils.Utils

@RequiresApi(Build.VERSION_CODES.O)
open class StandardProtocol : WatchProtocol {
    companion object : StandardProtocol()

    override fun extractKey(data: String): Int? {
        return runCatching { Utils.toIntArray(data)[0] }.getOrNull()
    }

    override fun unwrapPayload(data: String, key: Int): String {
        return data
    }

    override fun getWatchConditionRequest(): String {
        return "28"
    }

    override suspend fun setTime(timeMs: Long?, offset: Long?) {
        TimeIO.apply {
            writeDST()
            writeDSTForWorldCities()
            writeWorldCities()
            set(timeMs, offset)
        }
    }

    override suspend fun getTimer(): Int {
        return TimerIO.request(getTimerRequest())
    }

    override fun setTimer(timerValue: Int) {
        TimerIO.set(timerValue)
    }

    override fun getTimerRequest(): String {
        return "18"
    }

    override fun getTimerSize(): Int {
        return 7
    }

    override suspend fun getHomeTime(): String {
        val raw = WorldCitiesIO.request(0)
        return HomeTimeIOFunctional.parseHomeCity(raw, 2)
    }

    override suspend fun getBatteryLevel(): Int {
        return WatchConditionIO.request(getWatchConditionRequest()).batteryLevel
    }

    override suspend fun getWatchTemperature(): Int {
        return WatchConditionIO.request(getWatchConditionRequest()).temperature
    }

    override suspend fun getAlarms(): ArrayList<Alarm> {
        return AlarmsIO.request()
    }

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        AlarmsIO.set(alarms)
    }

    override suspend fun getSettings(): Settings {
        val settings = SettingsIO.request()
        val timeAdjustment = TimeAdjustmentIO.request()
        settings.timeAdjustment = timeAdjustment.isTimeAdjustmentSet
        settings.adjustmentTimeMinutes = timeAdjustment.adjustmentTimeMinutes
        return settings
    }

    override fun setSettings(settings: Settings) {
        SettingsIO.set(settings)
        TimeAdjustmentIO.set(settings)
    }

    override suspend fun getBasicSettings(): Settings {
        return SettingsIO.request()
    }

    override suspend fun getTimeAdjustment(): TimeAdjustmentInfo {
        return TimeAdjustmentIO.request()
    }
}
