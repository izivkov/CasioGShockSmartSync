package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import timber.log.Timber
import java.time.DayOfWeek
import java.time.Month


/*
This class is used during development to mock the GShock API.
 */
@RequiresApi(Build.VERSION_CODES.O)
class GShockAPIMock(private val context: Context) : IGShockAPI {

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            WatchInfo.setNameAndModel(getWatchName())
        }
    }

    override suspend fun waitForConnection(deviceId: String?) {
        ProgressEvents.onNext("DeviceName", "CASIO GW-5600")

        ProgressEvents.onNext("ConnectionStarted")

        ProgressEvents.onNext("WatchInitializationCompleted")
        ProgressEvents.onNext("ConnectionSetupComplete")
        ProgressEvents.onNext("ButtonPressedInfoReceived")
    }

    override suspend fun init(): Boolean {
        return true
    }

    override fun close() {
        // NO-OP
    }

    override fun isConnected(): Boolean {
        return true
    }

    override fun teardownConnection(device: BluetoothDevice) {
        // NO-OP
    }

    override suspend fun getPressedButton(): IO.WatchButton {
        return IO.WatchButton.LOWER_LEFT
    }

    override fun isActionButtonPressed(): Boolean {
        return false
    }

    override fun isNormalButtonPressed(): Boolean {
        return true
    }

    override fun isAutoTimeStarted(): Boolean {
        return false
    }

    override fun isFindPhoneButtonPressed(): Boolean {
        return false
    }

    override fun isAlwaysConnectedConnectionPressed(): Boolean {
        return false
    }

    override suspend fun getWatchName(): String {
        return "CASIO GW-5600"
        // return "CASIO ECB-30"
    }

    override suspend fun getError(): String {
        return "Error"
    }

    override suspend fun getDSTWatchState(state: IO.DstState): String {
        /*
        DST STATE ZERO: 0x1D 00 01 03 02 02 76 00 00 FF FF FF FF FF FF
        DST STATE TWO: 0x1D 02 03 03 03 A0 00 DC 00 FF FF FF FF FF FF
        DST STATE FOUR: 0x1D 04 05 02 03 7A 00 CA 00 FF FF FF FF FF FF
         */
        return "0x1D 00 01 03 02 02 76 00 00 FF FF FF FF FF FF"
    }

    override suspend fun getDSTForWorldCities(cityNumber: Int): String {
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

    override suspend fun getWorldCities(cityNumber: Int): String {
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

    override suspend fun getHomeTime(): String {
        return "SOFIA"
    }

    override suspend fun getBatteryLevel(): Int {
        return 81
    }

    override suspend fun getWatchTemperature(): Int {
        return 37
    }

    override suspend fun getTimer(): Int {
        return 4 * 60
    }

    override fun setTimer(timerValue: Int) {
    }

    override suspend fun getAppInfo(): String {
        return "0x22 C7 67 B2 F0 78 86 71 6A 76 EC 02"
    }

    override suspend fun setTime(timeZone: String, timeMs: Long?) {
        Timber.i("Time set to $timeZone")
    }

    override suspend fun getAlarms(): ArrayList<Alarm> {
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

    override fun setAlarms(alarms: ArrayList<Alarm>) {
        Timber.i("Alarms set: $alarms")
    }

    override suspend fun getEventsFromWatch(): ArrayList<Event> {

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

    override suspend fun getEventFromWatch(eventNumber: Int): Event {
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

    override fun setEvents(events: ArrayList<Event>) {
        Timber.i("Events set: $events")
    }

    override fun clearEvents() {
        Timber.i("Events cleared")
    }

    override suspend fun getSettings(): Settings {
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

    override suspend fun getBasicSettings(): Settings {
        return Settings()
    }

    override suspend fun getTimeAdjustment(): TimeAdjustmentInfo {
        return TimeAdjustmentInfo(true, 30)
    }

    override fun setSettings(settings: Settings) {
        Timber.i("Settings set: $settings")
    }

    override fun disconnect() {
        Timber.i("Disconnected")
    }

    override fun stopScan() {
        Timber.i("Scan stopped")
    }

    override fun isBluetoothEnabled(context: Context): Boolean {
        return false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun sendMessage(message: String) {
        Timber.i("Message sent: $message")
    }

    override fun sendAppNotification(notification: AppNotification) {
        Timber.i("sendAppNotification: Message sent: $notification")
    }

    override fun supportsAppNotifications(): Boolean {
        return true
    }

    override fun resetHand() {
        Timber.i("Hand reset")
    }

    override fun validateBluetoothAddress(deviceAddress: String?): Boolean {
        return true
    }

    override fun preventReconnection(): Boolean {
        return true
    }

    override suspend fun setScratchpadData(data: ByteArray, startIndex: Int) {}
    override suspend fun getScratchpadData(index: Int, length: Int): ByteArray {return byteArrayOf()}
    }