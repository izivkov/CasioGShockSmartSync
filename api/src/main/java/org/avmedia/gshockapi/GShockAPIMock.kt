package org.avmedia.gshockapi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import java.time.DayOfWeek
import java.time.Month
import java.util.TimeZone


/*
This class is used during development to mock the GShock API.
 */
@RequiresApi(Build.VERSION_CODES.O)
class GShockAPIMock(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            WatchInfo.setNameAndModel(getWatchName())
        }
    }

    suspend fun waitForConnection(deviceId: String? = "", deviceName: String? = "") {
//        ProgressEvents.onNext("WaitForConnection")
//        delay(1000)

        ProgressEvents.onNext("DeviceName", "CASIO GW-B5600")
        //delay(1000)

        ProgressEvents.onNext("ConnectionStarted")
        // delay(1000)

        ProgressEvents.onNext("WatchInitializationCompleted")
        ProgressEvents.onNext("ConnectionSetupComplete")
        // delay(1000)
        ProgressEvents.onNext("ButtonPressedInfoReceived")

        // delay(1000)
        // ProgressEvents.onNext()
        // delay(1000)
    }

    private suspend fun init(): Boolean {
        return true
    }

    fun close () {
    }

    fun isConnected(): Boolean {
        return true
    }

    fun teardownConnection(device: BluetoothDevice) {
        // NO-OP
    }

    suspend fun getPressedButton(): IO.WATCH_BUTTON {
        return IO.WATCH_BUTTON.LOWER_LEFT
    }

    fun isActionButtonPressed(): Boolean {
        return false
    }

    fun isNormalButtonPressed(): Boolean {
        return true
    }

    fun isAutoTimeStarted(): Boolean {
        return false
    }

    fun isFindPhoneButtonPressed(): Boolean {
        return false
    }

    suspend fun getWatchName(): String {
        return "CASIO GW-B5600"
        // return "CASIO ECB-30"
    }

    suspend fun getError(): String {
        return "Error"
    }

    suspend fun getDSTWatchState(state: IO.DTS_STATE): String {
        /*
        DST STATE ZERO: 0x1D 00 01 03 02 02 76 00 00 FF FF FF FF FF FF
        DST STATE TWO: 0x1D 02 03 03 03 A0 00 DC 00 FF FF FF FF FF FF
        DST STATE FOUR: 0x1D 04 05 02 03 7A 00 CA 00 FF FF FF FF FF FF
         */
        return "0x1D 00 01 03 02 02 76 00 00 FF FF FF FF FF FF"
    }

    suspend fun getDSTForWorldCities(cityNumber: Int): String {
        return when (cityNumber) {
            0 -> "0x1E 00 02 76 EC 04 01"
            1 -> "0x1E 01 00 00 00 00 00"
            2 -> "0x1E 02 A0 00 00 04 02"
            3 -> "0x1E 03 DC 00 04 04 02"
            4 -> "0x1E 04 7A 00 20 04 00"
            5 -> "0x1E 05 CA 00 EC 04 01"
            else -> ""
        }
    }

    suspend fun getWorldCities(cityNumber: Int): String {
        return when (cityNumber) {
            0 -> "0x1F 00 54 4F 52 4F 4E 54 4F 00 00 00 00 00 00 00 00 00 00 00"
            1 -> "0x1F 01 28 55 54 43 29 00 00 00 00 00 00 00 00 00 00 00 00 00"
            2 -> "0x1F 02 4C 4F 4E 44 4F 4E 00 00 00 00 00 00 00 00 00 00 00 00"
            3 -> "0x1F 03 50 41 52 49 53 00 00 00 00 00 00 00 00 00 00 00 00 00"
            4 -> "0x1F 04 48 4F 4E 47 20 4B 4F 4E 47 00 00 00 00 00 00 00 00 00"
            5 -> "00x1F 05 4E 45 57 20 59 4F 52 4B 00 00 00 00 00 00 00 00 00 00"
            else -> ""
        }
    }

    suspend fun getHomeTime(): String {
        return "SOFIA"
    }

    suspend fun getBatteryLevel(): Int {
        return 81
    }

    suspend fun getWatchTemperature(): Int {
        return 37
    }

    suspend fun getTimer(): Int {
        return 4 * 60
    }

    fun setTimer(timerValue: Int) {
        println("Timer set to $timerValue")
    }

    suspend fun getAppInfo(): String {
        return "0x22 C7 67 B2 F0 78 86 71 6A 76 EC 02"
    }

    suspend fun setTime(timeZone: String = TimeZone.getDefault().id, timeMs: Long? = null) {
        println("Time set to $timeZone")
    }

    suspend fun getAlarms(): ArrayList<Alarm> {
        delay(0)

        val alarmList: ArrayList<Alarm> = arrayListOf(
            Alarm(hour = 6, minute = 45, enabled = true, hasHourlyChime = true),
            Alarm(hour = 8, minute = 0, enabled = true, hasHourlyChime = false),
            Alarm(hour = 20, minute = 0, enabled = true, hasHourlyChime = false),
            Alarm(hour = 20, minute = 50, enabled = false, hasHourlyChime = false),
            Alarm(hour = 9, minute = 25, enabled = false, hasHourlyChime = false)
        )
        return alarmList
    }

    fun setAlarms(alarms: ArrayList<Alarm>) {
        println("Alarms set: $alarms")
    }

    suspend fun getEventsFromWatch(): ArrayList<Event> {

        val eventList: ArrayList<Event> = arrayListOf(
            Event(
                title = "Event 1",
                startDate = EventDate(
                    2024,
                    Month.MAY,
                    1
                ), // Replace with actual EventDate structure
                endDate = EventDate(2024, Month.MAY, 2),
                repeatPeriod = RepeatPeriod.NEVER,
                daysOfWeek = arrayListOf(DayOfWeek.MONDAY),
                enabled = false,
                incompatible = false
            ),
            Event(
                title = "Event 2",
                startDate = EventDate(
                    2024,
                    Month.MAY,
                    1
                ), // Replace with actual EventDate structure
                endDate = EventDate(2024, Month.MAY, 2),
                repeatPeriod = RepeatPeriod.NEVER,
                daysOfWeek = arrayListOf(DayOfWeek.MONDAY),
                enabled = false,
                incompatible = false
            ),
            Event(
                title = "Event 3",
                startDate = EventDate(
                    2024,
                    Month.MAY,
                    1
                ), // Replace with actual EventDate structure
                endDate = EventDate(2024, Month.MAY, 2),
                repeatPeriod = RepeatPeriod.NEVER,
                daysOfWeek = arrayListOf(DayOfWeek.MONDAY),
                enabled = false,
                incompatible = false
            ),
            Event(
                title = "Event 4",
                startDate = EventDate(
                    2024,
                    Month.MAY,
                    1
                ), // Replace with actual EventDate structure
                endDate = EventDate(2024, Month.MAY, 2),
                repeatPeriod = RepeatPeriod.NEVER,
                daysOfWeek = arrayListOf(DayOfWeek.MONDAY),
                enabled = false,
                incompatible = false
            )
        )

        return eventList

    }

    private suspend fun getEventFromWatch(eventNumber: Int): Event {

        return Event(
            title = "Event 10",
            startDate = EventDate(2024, Month.MAY, 1), // Replace with actual EventDate structure
            endDate = EventDate(2024, Month.MAY, 2),
            repeatPeriod = RepeatPeriod.NEVER,
            daysOfWeek = arrayListOf(DayOfWeek.MONDAY),
            enabled = false,
            incompatible = false
        )
    }

    fun setEvents(events: ArrayList<Event>) {
        println("Events set: $events")
    }

    fun clearEvents() {
        println("Events cleared")
    }

    suspend fun getSettings(): Settings {
        delay(0)

        val setting = Settings()
        setting.language = "Spanish"
        setting.timeFormat = "12h"
        setting.dateFormat = "MM:DD"

        setting.buttonTone = true
        setting.powerSavingMode = true
        setting.autoLight = true
        setting.lightDuration = "4s"

        setting.timeAdjustment = true
        setting.adjustmentTimeMinutes = 29

        return setting
    }

    private suspend fun getBasicSettings(): Settings {
        return Settings()
    }

    private suspend fun getTimeAdjustment(): TimeAdjustmentInfo {
        return TimeAdjustmentInfo(true, 30)
    }

    fun setSettings(settings: Settings) {
        println("Settings set: $settings")
    }

    fun disconnect() {
        println("Disconnected")
    }

    fun stopScan() {
        println("Scan stopped")
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendMessage(message: String) {
        println("Message sent: $message")
    }

    fun resetHand() {
        println("Hand reset")
    }

    fun validateBluetoothAddress(deviceAddress: String?): Boolean {
        return true
    }

    fun preventReconnection(): Boolean {
        return true
    }
}