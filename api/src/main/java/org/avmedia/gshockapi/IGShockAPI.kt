package org.avmedia.gshockapi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import org.avmedia.gshockapi.io.IO
import org.avmedia.gshockapi.io.TimeAdjustmentInfo
import java.util.TimeZone

/**
 * Main interface for communicating with Casio G-Shock watches over Bluetooth.
 * This interface defines all available operations for device discovery, connection management,
 * and watch feature interaction (time, alarms, reminders, settings, etc.).
 */
interface IGShockAPI {
    /**
     * This function waits for the watch to connect to the phone.
     * When connected, it returns and emits a `ConnectionSetupComplete` event.
     *
     * @param deviceId Optional Bluetooth MAC address of a watch to connect to.
     *                 Providing this skips general discovery and connects directly.
     */
    suspend fun waitForConnection(deviceId: String? = "")

    /**
     * Initializes the connection and retrieves basic watch information.
     * Should be called automatically after connection is established.
     *
     * @return true if initialization was successful.
     */
    suspend fun init(): Boolean

    /**
     * Checks if the watch is currently connected to the phone.
     *
     * @return true if connected.
     */
    fun isConnected(): Boolean

    /**
     * Internal function to teardown the Bluetooth connection for a specific device.
     *
     * @param device The BluetoothDevice to disconnect.
     */
    fun teardownConnection(device: BluetoothDevice)

    /**
     * Retrieves the button that was pressed on the watch to initiate the connection.
     *
     * @return The watch button type (LOWER_LEFT, LOWER_RIGHT, NO_BUTTON, etc.).
     */
    suspend fun getPressedButton(): IO.WatchButton

    /**
     * Checks if the connection was initiated by a short-press of the lower-right button.
     *
     * @return true if the action button was pressed.
     */
    fun isActionButtonPressed(): Boolean

    /**
     * Checks if the connection was initiated by the "Always Connected" button pattern.
     *
     * @return true if always-connected pattern was used.
     */
    fun isAlwaysConnectedConnectionPressed(): Boolean

    /**
     * Checks if the connection was initiated by a long-press of the lower-left button.
     *
     * @return true if the normal connection button was pressed.
     */
    fun isNormalButtonPressed(): Boolean

    /**
     * Checks if the connection was initiated automatically by the watch (Auto-Time).
     *
     * @return true if this is an automated periodic connection.
     */
    fun isAutoTimeStarted(): Boolean

    /**
     * Checks if the connection was initiated by the "Find Phone" long-press of the lower-right button.
     *
     * @return true if find-phone button was pressed.
     */
    fun isFindPhoneButtonPressed(): Boolean

    /**
     * Retrieves the watch model name (e.g., "GW-B5600").
     *
     * @return The model name as reported by the watch.
     */
    suspend fun getWatchName(): String

    /**
     * Retrieves the last error reported by the watch or the BLE layer.
     *
     * @return Error message string.
     */
    suspend fun getError(): String

    /**
     * Retrieves the DST state for a specific DST configuration on the watch.
     *
     * @param state The DST state type to query (ZERO, TWO, FOUR).
     * @return The DST state as a string.
     */
    suspend fun getDSTWatchState(state: IO.DstState): String

    /**
     * Retrieves the DST setting for a specific World City.
     *
     * @param cityNumber The index of the world city (0 to 5).
     * @return The DST state for the requested city.
     */
    suspend fun getDSTForWorldCities(cityNumber: Int): String

    /**
     * Retrieves the name of a specific World City configured on the watch.
     *
     * @param cityNumber The index of the world city (0 to 5).
     * @return The name of the world city.
     */
    suspend fun getWorldCities(cityNumber: Int): String

    /**
     * Retrieves the Home City (Home Time) configured on the watch.
     *
     * @return The name of the home city.
     */
    suspend fun getHomeTime(): String

    /**
     * Retrieves the current battery level of the watch.
     *
     * @return Battery percentage (0 to 100).
     */
    suspend fun getBatteryLevel(): Int

    /**
     * Retrieves the current temperature from the watch's sensor.
     *
     * @return Temperature in degrees Celsius.
     */
    suspend fun getWatchTemperature(): Int

    /**
     * Retrieves the daily step count from the watch (if supported).
     * This feature is only available on compatible watch models like ABL-100WE.
     *
     * @return Daily step count, or 0 if the feature is not supported or unavailable.
     */
    suspend fun getStepCount(): Int

    /**
     * Retrieves the current timer setting in seconds.
     *
     * @return Timer value in seconds.
     */
    suspend fun getTimer(): Int

    /**
     * Sets the countdown timer on the watch.
     *
     * @param timerValue The timer duration in seconds.
     */
    fun setTimer(timerValue: Int)

    /**
     * Retrieves internal application information from the watch.
     *
     * @return App info string.
     */
    suspend fun getAppInfo(): String

    /**
     * Synchronizes the watch's time with the phone's time.
     *
     * @param timeZone The target timezone ID (e.g., "Europe/Sofia"). Defaults to phone's current timezone.
     * @param timeMs Optional timestamp in milliseconds to set. Defaults to current time.
     */
    suspend fun setTime(timeZone: String = TimeZone.getDefault().id, timeMs: Long? = null)

    /**
     * Retrieves the list of alarms from the watch.
     *
     * @return An ArrayList of [Alarm] objects.
     */
    suspend fun getAlarms(): ArrayList<Alarm>

    /**
     * Updates the alarms on the watch.
     *
     * @param alarms The list of 5 alarms to set.
     */
    fun setAlarms(alarms: ArrayList<Alarm>)

    /**
     * Retrieves all events (reminders) stored on the watch.
     *
     * @return An ArrayList of [Event] objects.
     */
    suspend fun getEventsFromWatch(): ArrayList<Event>

    /**
     * Retrieves a single event (reminder) by its index.
     *
     * @param eventNumber The event index (1 to 5).
     * @return The requested [Event].
     */
    suspend fun getEventFromWatch(eventNumber: Int): Event

    /**
     * Updates the events (reminders) on the watch.
     *
     * @param events The list of 5 events to set.
     */
    fun setEvents(events: ArrayList<Event>)

    /**
     * Clears all events (reminders) from the watch.
     */
    fun clearEvents()

    /**
     * Retrieves the complete settings profile from the watch, including basic settings and time adjustment info.
     *
     * @return A [Settings] object.
     */
    suspend fun getSettings(): Settings

    /**
     * Retrieves the basic settings (date format, language, button tones, etc.) from the watch.
     *
     * @return A [Settings] object containing basic configuration.
     */
    suspend fun getBasicSettings(): Settings

    /**
     * Retrieves time adjustment (Auto-Time) configuration from the watch.
     *
     * @return A [TimeAdjustmentInfo] object.
     */
    suspend fun getTimeAdjustment(): TimeAdjustmentInfo

    /**
     * Updates the watch's configuration settings.
     *
     * @param settings The [Settings] object containing the new configuration.
     */
    fun setSettings(settings: Settings)

    /**
     * Disconnects from the current watch.
     */
    fun disconnect()

    /**
     * Closes the API and releases all underlying Bluetooth resources.
     */
    fun close()

    /**
     * Checks if Bluetooth is enabled on the phone.
     *
     * @param context Android context.
     * @return true if Bluetooth is ON.
     */
    fun isBluetoothEnabled(context: Context): Boolean

    /**
     * Sends a rich notification to the watch display.
     *
     * @param notification The [AppNotification] object to send.
     */
    fun sendAppNotification(notification: AppNotification)

    /**
     * Checks if the connected watch supports receiving app notifications.
     *
     * @return true if the notification service is supported.
     */
    fun supportsAppNotifications(): Boolean

    /**
     * Writes data to the watch's scratchpad/user data area.
     *
     * @param data The byte array to write.
     */
    suspend fun setScratchpadData(data: ByteArray)

    /**
     * Sends a raw JSON message to the watch. Use for custom actions not exposed by the API.
     *
     * @param message The JSON message string.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun sendMessage(message: String)

    /**
     * Resets the watch hands to their home position.
     */
    fun resetHand()

    /**
     * Validates if a string is a valid Bluetooth MAC address.
     *
     * @param deviceAddress The address to validate.
     * @return true if the address format is valid.
     */
    fun validateBluetoothAddress(deviceAddress: String?): Boolean

    /**
     * Signal to prevent the app from immediately attempting to reconnect.
     *
     * @return true if reconnection should be prevented.
     */
    fun preventReconnection(): Boolean

    /**
     * Reads data from the watch's scratchpad/user data area.
     *
     * @return The retrieved byte array.
     */
    suspend fun getScratchpadData(): ByteArray

    /**
     * Checks if the scratchpad data was reset since the last read.
     *
     * @return true if scratchpad was reset.
     */
    fun isScratchpadReset(): Boolean

    /**
     * Initiates the Companion Device Manager association process for pairing a new watch.
     *
     * @param context Android context.
     * @param delegate Callback to handle chooser readiness or errors.
     */
    fun associate(context: Context, delegate: ICDPDelegate)

    /**
     * Removes an existing device association.
     *
     * @param context Android context.
     * @param address The MAC address of the device to disassociate.
     */
    fun disassociate(context: Context, address: String)

    /**
     * Data class representing a device association.
     */
    data class Association(val address: String, val name: String?)

    /**
     * Retrieves a list of all associated devices with their stored names.
     *
     * @param context Android context.
     * @return List of [Association] objects.
     */
    fun getAssociationsWithNames(context: Context): List<Association>

    /**
     * Retrieves a list of MAC addresses for all associated devices.
     *
     * @param context Android context.
     * @return List of Bluetooth MAC address strings.
     */
    fun getAssociations(context: Context): List<String>

    /**
     * Starts background observation of a device's presence.
     * Only supported on Android 12 (API 31) and above.
     *
     * @param context Android context.
     * @param address MAC address of the device to observe.
     */
    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    fun startObservingDevicePresence(context: Context, address: String)

    /**
     * Stops background observation of a device's presence.
     *
     * @param context Android context.
     * @param address MAC address of the device to stop observing.
     */
    @RequiresPermission("android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE")
    @RequiresApi(Build.VERSION_CODES.S)
    fun stopObservingDevicePresence(context: Context, address: String)

    /**
     * Scans for G-Shock devices in the foreground.
     *
     * @param context Android context.
     * @param filter Lambda to filter discovered devices.
     * @param onDeviceFound Callback invoked for each matching device discovered.
     */
    fun scan(context: Context, filter: (DeviceInfo) -> Boolean, onDeviceFound: (DeviceInfo) -> Unit)

    /**
     * Stops an active foreground scan.
     */
    fun stopScan()

    /**
     * Starts a fallback scan using PendingIntent for background discovery of specific addresses.
     *
     * @param context Android context.
     * @param addresses List of MAC addresses to scan for.
     * @param pendingIntent Intent to trigger when a matching device is found.
     */
    fun startFallbackScan(context: Context, addresses: List<String>, pendingIntent: android.app.PendingIntent)
}
