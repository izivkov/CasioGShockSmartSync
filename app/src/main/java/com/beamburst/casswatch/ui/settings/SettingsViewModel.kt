package com.beamburst.casswatch.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.beamburst.casswatch.R
import com.beamburst.casswatch.data.repository.GShockRepository
import com.beamburst.casswatch.ui.common.AppSnackbar
import com.beamburst.casswatch.utils.LocalDataStorage
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.json.JSONObject

abstract class Setting(val name: String) {
    open suspend fun save() {} // Default empty implementation
}

data class SettingsState(
        val settings: List<Setting> = emptyList(),
        val settingsMap: Map<Class<out Setting>, Setting> = emptyMap()
)

sealed class SettingsAction {
    data class UpdateSetting<T : Setting>(val setting: T) : SettingsAction()
    data object SendToWatch : SettingsAction()
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
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
        val newSettings =
                arrayListOf(
                        Locale(),
                        OperationSound(),
                        Light(),
                        PowerSavingMode(),
                        Font(),
                        TimeAdjustment(appContext),
                )
        updateSettingsAndMap(filter(newSettings))

        /**
         * Launches a coroutine in the ViewModel's scope using the provided dispatcher. The
         * coroutine is automatically cancelled when the ViewModel is cleared.
         *
         * @param dispatcher The dispatcher to run the coroutine on (Dispatchers.Default for
         * CPU-intensive work)
         * @param block The coroutine code to execute
         *
         * Note: viewModelScope is an extension property provided by the lifecycle-viewmodel-ktx
         * library that creates a CoroutineScope tied to the ViewModel's lifecycle.
         */
        viewModelScope.launch(Dispatchers.Default) {
            if (!api.isConnected()) return@launch

            // Convert API settings to JSON object
            val settingsJson = Gson().toJsonTree(api.getSettings()).asJsonObject

            // Convert merged settings to string and update state
            val settingStr = Gson().toJson(settingsJson)
            updateSettingsAndMap(fromJson(settingStr))
        }
    }

    private fun updateSettingsAndMap(newSettings: ArrayList<Setting>) {
        val newMap = newSettings.associateBy { it::class.java }
        _state.update { it.copy(settings = newSettings, settingsMap = newMap) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Setting> getSetting(type: Class<T>): T {
        return state.value.settingsMap[type] as T
    }

    /**
     * Updates a single setting in both the settings list and settings map, then persists the
     * change.
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
            _state.update { it.copy(settings = currentList, settingsMap = newMap) }
            viewModelScope.launch { updatedSetting.save() }
        }
    }

    data class Locale(
            var timeFormat: TimeFormat = TimeFormat.TWELVE_HOURS,
            var dateFormat: DateFormat = DateFormat.MONTH_DAY,
            var dayOfWeekLanguage: DayOfWeekLanguage = DayOfWeekLanguage.ENGLISH,
    ) : Setting("Locale") {
        enum class TimeFormat(val value: String) {
            TWELVE_HOURS("12h"),
            TWENTY_FOUR_HOURS("24h"),
        }

        enum class DateFormat(val value: String) {
            MONTH_DAY("MM:DD"),
            DAY_MONTH("DD:MM"),
        }

        enum class DayOfWeekLanguage(val nativeName: String, val englishName: String) {
            ENGLISH("English", "English"),
            SPANISH("Español", "Spanish"),
            FRENCH("Français", "French"),
            GERMAN("Deutsch", "German"),
            ITALIAN("Italiano", "Italian"),
            RUSSIAN("Русский", "Russian")
        }
    }

    data class OperationSound(var sound: Boolean = true, var vibrate: Boolean = false) :
            Setting("Button Sound")

    data class Light(
            var autoLight: Boolean = false,
            var duration: LightDuration = LightDuration.ONE_POINT_FIVE_SECONDS,
    ) : Setting("Light") {
        enum class LightDuration(val value: String) {
            ONE_POINT_FIVE_SECONDS("1.5s"),
            THREE_SECONDS("3s")
        }
    }

    data class PowerSavingMode(var powerSavingMode: Boolean = false) : Setting("Power Saving Mode")

    data class TimeAdjustment(
            val appContext: Context,
            var timeAdjustment: Boolean = true,
            var adjustmentTimeMinutes: Int = 0,
            var timeAdjustmentNotifications: Boolean =
                    LocalDataStorage.getTimeAdjustmentNotification(appContext),
            var fineAdjustment: Int = LocalDataStorage.getFineTimeAdjustment(appContext),
    ) : Setting("Time Adjustment") {
        override suspend fun save() {
            LocalDataStorage.setTimeAdjustmentNotification(appContext, timeAdjustmentNotifications)
            LocalDataStorage.setFineTimeAdjustment(appContext, fineAdjustment)
        }
    }

    data class DnD(
            var dnd: Boolean = true,
    ) : Setting("DnD")

    data class Font(var font: FontType = FontType.STANDARD) : Setting("Font") {
        enum class FontType(val value: String) {
            STANDARD("Standard"),
            CLASSIC("Classic")
        }
    }

    private fun filter(settings: ArrayList<Setting>): ArrayList<Setting> {
        return settings.filter { setting ->
            when (setting) {
                is PowerSavingMode -> WatchInfo.hasPowerSavingMode
                is Font -> WatchInfo.hasMultipleFonts
                else -> true
            }
        } as
                ArrayList<Setting>
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
                "font" -> handleFont(value, updatedObjects)
            }
        }

        return ArrayList(updatedObjects)
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
        setting.duration =
                if (value == Light.LightDuration.ONE_POINT_FIVE_SECONDS.value) {
                    Light.LightDuration.ONE_POINT_FIVE_SECONDS
                } else {
                    Light.LightDuration.THREE_SECONDS
                }
        updatedObjects.add(setting)
    }

    private fun handleTimeFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.timeFormat =
                if (value == Locale.TimeFormat.TWELVE_HOURS.value) {
                    Locale.TimeFormat.TWELVE_HOURS
                } else {
                    Locale.TimeFormat.TWENTY_FOUR_HOURS
                }
        updatedObjects.add(setting)
    }

    private fun handleDateFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.dateFormat =
                if (value == Locale.DateFormat.MONTH_DAY.value) {
                    Locale.DateFormat.MONTH_DAY
                } else {
                    Locale.DateFormat.DAY_MONTH
                }
        updatedObjects.add(setting)
    }

    private fun handleLanguage(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = state.value.settingsMap[Locale::class.java] as Locale
        setting.dayOfWeekLanguage =
                when (value) {
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

    private fun handleFont(value: Any, updatedObjects: MutableSet<Setting>) {
        if (WatchInfo.hasMultipleFonts) {
            val setting = state.value.settingsMap[Font::class.java] as Font
            setting.font =
                    if (value == Font.FontType.CLASSIC.value) {
                        Font.FontType.CLASSIC
                    } else {
                        Font.FontType.STANDARD
                    }
            updatedObjects.add(setting)
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

        val timeAdjustment: TimeAdjustment =
                state.value.settingsMap[TimeAdjustment::class.java] as TimeAdjustment
        settings.timeAdjustment = timeAdjustment.timeAdjustment
        settings.adjustmentTimeMinutes = timeAdjustment.adjustmentTimeMinutes

        if (WatchInfo.hasMultipleFonts) {
            val fontSetting: Font = state.value.settingsMap[Font::class.java] as Font
            settings.font = fontSetting.font.value
        }

        viewModelScope.launch {
            runCatching {
                if (!api.isConnected()) {
                    AppSnackbar(appContext.getString(R.string.watch_not_connected))
                    return@runCatching
                }

                api.setSettings(settings)
                AppSnackbar(appContext.getString(R.string.settings_sent_to_watch))
            }
                    .onFailure { e ->
                        AppSnackbar(e.message ?: "Api Error")
                    }
        }
    }
}
