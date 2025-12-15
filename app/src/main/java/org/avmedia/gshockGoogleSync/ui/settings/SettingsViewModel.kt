package org.avmedia.gshockGoogleSync.ui.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.json.JSONObject
import java.text.SimpleDateFormat
import javax.inject.Inject

abstract class Setting(val name: String) {
    open fun save() {} // Default empty implementation
}

data class SettingsState(
    val settings: List<Setting> = emptyList(),
    val settingsMap: Map<Class<out Setting>, Setting> = emptyMap()
)

sealed class SettingsAction {
    data class UpdateSetting<T : Setting>(val setting: T) : SettingsAction()
    data object SetSmartDefaults : SettingsAction()
    data object SendToWatch : SettingsAction()
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: GShockRepository,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {

    fun onSettingUpdated(setting: Setting) {
        updateSetting(setting)
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        initializeSettings()
    }

    private fun initializeSettings() {
        val newSettings = arrayListOf(
            Locale(),
            OperationSound(),
            Light(),
            PowerSavingMode(),
            TimeAdjustment(appContext),
            KeepAlive(appContext)
        )
        updateSettingsAndMap(filter(newSettings))

        /**
         * Launches a coroutine in the ViewModel's scope using the provided dispatcher.
         * The coroutine is automatically cancelled when the ViewModel is cleared.
         *
         * @param dispatcher The dispatcher to run the coroutine on (Dispatchers.Default for CPU-intensive work)
         * @param block The coroutine code to execute
         *
         * Note: viewModelScope is an extension property provided by the lifecycle-viewmodel-ktx library
         * that creates a CoroutineScope tied to the ViewModel's lifecycle.
         */
        viewModelScope.launch(Dispatchers.Default) {            // Convert API settings to JSON object
            val settingsJson = Gson().toJsonTree(api.getSettings()).asJsonObject

            // We have additional settings defined in the APP, not in the API, so we need to merge them.
            class AppSettings(appContext: Context) {
                var keepAlive = LocalDataStorage.getKeepAlive(appContext)
            }

            val appSettingsJson = Gson().toJsonTree(AppSettings(appContext)).asJsonObject

            // Merge default settings into API settings
            for (entry in appSettingsJson.entrySet()) {
                settingsJson.add(entry.key, entry.value)
            }

            // Convert merged settings to string and update state
            val settingStr = Gson().toJson(settingsJson)
            updateSettingsAndMap(fromJson(settingStr))
        }
    }

    private fun updateSettingsAndMap(newSettings: ArrayList<Setting>) {
        val newMap = newSettings.associateBy { it::class.java }
        _state.update {
            it.copy(
                settings = newSettings,
                settingsMap = newMap
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Setting> getSetting(type: Class<T>): T {
        return state.value.settingsMap[type] as T
    }

    /**
     * Updates a single setting in both the settings list and settings map, then persists the change.
     *
     * @param updatedSetting The new setting instance that will replace the existing one
     *
     * The function:
     * 1. Makes a mutable copy of the current settings list
     * 2. Finds the matching setting by its class type
     * 3. Updates both the list and map with the new setting
     * 4. Updates the state with the new collections
     * 5. Calls save() on the setting to persist changes
     *
     * Note: If no matching setting is found (index == -1), no update occurs
     */
    private fun updateSetting(updatedSetting: Setting) {
        val currentList = state.value.settings.toMutableList()
        val index = currentList.indexOfFirst { it::class == updatedSetting::class }
        if (index != -1) {
            currentList[index] = updatedSetting
            val newMap = state.value.settingsMap.toMutableMap()
            newMap[updatedSetting::class.java] = updatedSetting
            _state.update {
                it.copy(
                    settings = currentList,
                    settingsMap = newMap
                )
            }
            updatedSetting.save()
        }
    }

    data class Locale(
        var timeFormat: TimeFormat = TimeFormat.TWELVE_HOURS,
        var dateFormat: DateFormat = DateFormat.MONTH_DAY,
        var dayOfWeekLanguage: DayOfWeekLanguage = DayOfWeekLanguage.ENGLISH,
    ) : Setting("Locale") {
        enum class TimeFormat(val value: String) {
            TWELVE_HOURS("12h"), TWENTY_FOUR_HOURS("24h"),
        }

        enum class DateFormat(val value: String) {
            MONTH_DAY("MM:DD"), DAY_MONTH("DD:MM"),
        }

        enum class DayOfWeekLanguage(val nativeName: String, val englishName: String) {
            ENGLISH("English", "English"),
            SPANISH("Español", "Spanish"),
            FRENCH("Français", "French"),
            GERMAN("Deutsch", "German"),
            ITALIAN("Italiano", "Italian"),
            RUSSIAN("Русский", "Russian");
        }
    }

    data class OperationSound(var sound: Boolean = true, var vibrate: Boolean = false) :
        Setting("Button Sound")

    data class Light(
        var autoLight: Boolean = false,
        var duration: LightDuration = LightDuration.TWO_SECONDS,
    ) : Setting("Light") {
        enum class LightDuration(val value: String) {
            TWO_SECONDS("2s"), FOUR_SECONDS("4s")
        }
    }

    data class PowerSavingMode(var powerSavingMode: Boolean = false) :
        Setting("Power Saving Mode")

    data class KeepAlive(
        val appContext: Context,
        var keepAlive: Boolean = LocalDataStorage.getKeepAlive(appContext)
    ) :
        Setting("Run in Background") {

        override fun save() {
            LocalDataStorage.setKeepAlive(
                appContext,
                keepAlive
            )
        }
    }

    data class TimeAdjustment(
        val appContext: Context,
        var timeAdjustment: Boolean = true,
        var adjustmentTimeMinutes: Int = 0,
        var timeAdjustmentNotifications: Boolean =
            LocalDataStorage.getTimeAdjustmentNotification(appContext),
        var fineAdjustment: Int = LocalDataStorage.getFineTimeAdjustment(appContext),
    ) : Setting("Time Adjustment") {
        override fun save() {
            LocalDataStorage.setTimeAdjustmentNotification(
                appContext,
                timeAdjustmentNotifications
            )
            LocalDataStorage.setFineTimeAdjustment(
                appContext,
                fineAdjustment
            )
        }
    }

    data class DnD(
        var dnd: Boolean = true,
    ) : Setting("DnD")

    private fun filter(settings: ArrayList<Setting>): ArrayList<Setting> {
        return settings.filter { setting ->
            when (setting) {
                is PowerSavingMode -> WatchInfo.hasPowerSavingMode
                else -> true
            }
        } as ArrayList<Setting>
    }

    @Synchronized
    fun fromJson(jsonStr: String): ArrayList<Setting> {
        val updatedObjects = mutableSetOf<Setting>()
        val jsonObj = JSONObject(jsonStr)
        val keys = jsonObj.keys()

        while (keys.hasNext()) {
            val key: String = keys.next()
            val value = jsonObj.get(key)

            when (key) {
                "powerSavingMode" -> handlePowerSavingMode(value, updatedObjects)
                "timeAdjustment" -> handleTimeAdjustment(value, updatedObjects)
                "adjustmentTimeMinutes" -> handleAdjustmentTimeMinutes(value, updatedObjects)
                "buttonTone" -> handleButtonTone(value, updatedObjects)
                "keyVibration" -> handleButtonVibration(value, updatedObjects)
                "autoLight" -> handleAutoLight(value, updatedObjects)
                "lightDuration" -> handleLightDuration(value, updatedObjects)
                "timeFormat" -> handleTimeFormat(value, updatedObjects)
                "dateFormat" -> handleDateFormat(value, updatedObjects)
                "language" -> handleLanguage(value, updatedObjects)
                "keepAlive" -> handleRunInBackground(value, updatedObjects)
            }
        }

        return ArrayList(updatedObjects)
    }

    private fun handleRunInBackground(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[KeepAlive::class.java] as KeepAlive
        setting.keepAlive = value == true
        updatedObjects.add(setting)
    }

    private fun handlePowerSavingMode(value: Any, updatedObjects: MutableSet<Setting>) {
        if (WatchInfo.hasPowerSavingMode) {
            val setting = state.value.settingsMap[PowerSavingMode::class.java] as PowerSavingMode
            setting.powerSavingMode = value == true
            updatedObjects.add(setting)
        }
    }

    private fun handleTimeAdjustment(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[TimeAdjustment::class.java] as TimeAdjustment
        setting.timeAdjustment = value == true
        updatedObjects.add(setting)
    }

    private fun handleAdjustmentTimeMinutes(value: Any, updatedObjects: MutableSet<Setting>) {
        if (!WatchInfo.alwaysConnected) {
            val setting = state.value.settingsMap[TimeAdjustment::class.java] as TimeAdjustment
            setting.adjustmentTimeMinutes = value as Int
            updatedObjects.add(setting)
        }
    }

    private fun handleButtonTone(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[OperationSound::class.java] as OperationSound
        setting.sound = value == true
        updatedObjects.add(setting)
    }

    private fun handleButtonVibration(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[OperationSound::class.java] as OperationSound
        setting.vibrate = value == true
        updatedObjects.add(setting)
    }

    private fun handleAutoLight(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Light::class.java] as Light
        setting.autoLight = value == true
        updatedObjects.add(setting)
    }

    private fun handleLightDuration(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Light::class.java] as Light
        setting.duration = if (value == Light.LightDuration.TWO_SECONDS.value) {
            Light.LightDuration.TWO_SECONDS
        } else {
            Light.LightDuration.FOUR_SECONDS
        }
        updatedObjects.add(setting)
    }

    private fun handleTimeFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.timeFormat = if (value == Locale.TimeFormat.TWELVE_HOURS.value) {
            Locale.TimeFormat.TWELVE_HOURS
        } else {
            Locale.TimeFormat.TWENTY_FOUR_HOURS
        }
        updatedObjects.add(setting)
    }

    private fun handleDateFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.dateFormat = if (value == Locale.DateFormat.MONTH_DAY.value) {
            Locale.DateFormat.MONTH_DAY
        } else {
            Locale.DateFormat.DAY_MONTH
        }
        updatedObjects.add(setting)
    }

    private fun handleLanguage(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.dayOfWeekLanguage = when (value) {
            Locale.DayOfWeekLanguage.ENGLISH.englishName -> Locale.DayOfWeekLanguage.ENGLISH
            Locale.DayOfWeekLanguage.SPANISH.englishName -> Locale.DayOfWeekLanguage.SPANISH
            Locale.DayOfWeekLanguage.FRENCH.englishName -> Locale.DayOfWeekLanguage.FRENCH
            Locale.DayOfWeekLanguage.GERMAN.englishName -> Locale.DayOfWeekLanguage.GERMAN
            Locale.DayOfWeekLanguage.ITALIAN.englishName -> Locale.DayOfWeekLanguage.ITALIAN
            Locale.DayOfWeekLanguage.RUSSIAN.englishName -> Locale.DayOfWeekLanguage.RUSSIAN
            else -> setting.dayOfWeekLanguage // No change if the language is unknown
        }
        updatedObjects.add(setting)
    }

    @SuppressLint("SimpleDateFormat")
    private suspend fun getSmartDefaults(): ArrayList<Setting> {
        val smartSettings = arrayListOf<Setting>()
        val currentLocale = java.util.Locale.getDefault()

        // Locale
        val language = when (currentLocale.language) {
            "en" -> Locale.DayOfWeekLanguage.ENGLISH
            "es" -> Locale.DayOfWeekLanguage.SPANISH
            "fr" -> Locale.DayOfWeekLanguage.FRENCH
            "de" -> Locale.DayOfWeekLanguage.GERMAN
            "it" -> Locale.DayOfWeekLanguage.ITALIAN
            "ru" -> Locale.DayOfWeekLanguage.RUSSIAN
            else -> Locale.DayOfWeekLanguage.ENGLISH
        }

        val dateTimePattern = SimpleDateFormat().toPattern()
        val datePattern = dateTimePattern.split(" ")[0]
        val timePattern = dateTimePattern.split(" ")[1]

        val dateFormat = if (datePattern.lowercase().startsWith("d")) {
            Locale.DateFormat.DAY_MONTH
        } else {
            Locale.DateFormat.MONTH_DAY
        }
        val timeFormat = if (timePattern[0] == 'h') {
            Locale.TimeFormat.TWELVE_HOURS
        } else {
            Locale.TimeFormat.TWENTY_FOUR_HOURS
        }
        val locale =
            Locale(
                timeFormat = timeFormat,
                dateFormat = dateFormat,
                dayOfWeekLanguage = language
            )
        smartSettings.add(locale)

        // Button sounds
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val buttonTone =
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val operationSound = OperationSound(buttonTone)
        smartSettings.add(operationSound)

        // Light settings
        val autoLight = false
        val light = Light(autoLight, Light.LightDuration.TWO_SECONDS)
        smartSettings.add(light)

        // Power Save Mode
        if (WatchInfo.hasPowerSavingMode) {
            val batteryLevel = api.getBatteryLevel()
            val currentPowerSavingMode: PowerSavingMode =
                state.value.settingsMap[PowerSavingMode::class.java] as PowerSavingMode

            val enablePowerSetting = batteryLevel <= 15 || currentPowerSavingMode.powerSavingMode
            val powerSavings = PowerSavingMode(enablePowerSetting)
            smartSettings.add(powerSavings)
        }

        // Time adjustment
        val notifyMe = LocalDataStorage.getTimeAdjustmentNotification(appContext)
        val timeAdjustment = TimeAdjustment(
            appContext = appContext,
            timeAdjustment = true,
            adjustmentTimeMinutes = 30,
            timeAdjustmentNotifications = notifyMe,
            fineAdjustment = 0  // Explicitly set to 0
        )
        smartSettings.add(timeAdjustment)

        // Run in background
        val keepAliveValue = LocalDataStorage.getKeepAlive(appContext)
        val keepAlive = KeepAlive(appContext, keepAliveValue)
        smartSettings.add(keepAlive)

        return smartSettings
    }

    fun setSmartDefaults() {
        viewModelScope.launch {
            runCatching {
                updateSettingsAndMap(getSmartDefaults())

                // Save all local storage settings, in case the user abandons the screen.
                // Local storage setting are not sent to the watch, but are used by the app.
                // Example: keepAlive setting.
                state.value.settings.forEach(Setting::save)

            }.onFailure { e ->
                _uiEvents.emit(UiEvent.ShowSnackbar(e.message ?: "Error"))
            }
        }
    }

    fun sendToWatch() {
        val settings = Settings()

        val localeSetting: Locale = state.value.settingsMap[Locale::class.java] as Locale
        settings.language = localeSetting.dayOfWeekLanguage.englishName
        settings.timeFormat = localeSetting.timeFormat.value
        settings.dateFormat = localeSetting.dateFormat.value

        val lightSetting: Light = state.value.settingsMap[Light::class.java] as Light
        settings.autoLight = lightSetting.autoLight
        settings.lightDuration = lightSetting.duration.value

        if (WatchInfo.hasPowerSavingMode) {
            val powerSavingMode: PowerSavingMode =
                state.value.settingsMap[PowerSavingMode::class.java] as PowerSavingMode
            settings.powerSavingMode = powerSavingMode.powerSavingMode
        }

        val buttonTone: OperationSound =
            state.value.settingsMap[OperationSound::class.java] as OperationSound
        settings.buttonTone = buttonTone.sound
        settings.keyVibration = buttonTone.vibrate

        val timeAdjustment: TimeAdjustment = state.value.settingsMap[TimeAdjustment::class.java] as TimeAdjustment
        settings.timeAdjustment = timeAdjustment.timeAdjustment
        settings.adjustmentTimeMinutes = timeAdjustment.adjustmentTimeMinutes

        viewModelScope.launch {
            runCatching {
                api.setSettings(settings)
                _uiEvents.emit(UiEvent.ShowSnackbar(appContext.getString(R.string.settings_sent_to_watch)))
            }.onFailure { e ->
                _uiEvents.emit(UiEvent.ShowSnackbar(e.message ?: "Api Error"))
            }
        }
    }
}
