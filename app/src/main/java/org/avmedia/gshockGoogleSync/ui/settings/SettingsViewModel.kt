package org.avmedia.gshockGoogleSync.ui.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: GShockRepository,
    @ApplicationContext val appContext: Context // Inject application context
) : ViewModel() {

    class AppSettings(appContext: Context) {
        var keepAlive = LocalDataStorage.getKeepAlive(appContext)
    }

    private val appSettings = AppSettings(appContext)

    abstract class Setting(open var title: String) {
        open fun save() {
            // NO-OP
        }
    }

    private val _settingsFlow = MutableStateFlow<List<Setting>>(emptyList())
    val settings: StateFlow<List<Setting>> = _settingsFlow
    private val settingsMap = ConcurrentHashMap<Class<out Setting>, Setting>()

    private fun updateSettingsAndMap(newSettings: ArrayList<Setting>) {
        settingsMap.clear()
        newSettings.forEach { setting ->
            settingsMap[setting::class.java] = setting
        }
        _settingsFlow.value = newSettings
    }

    fun <T : Setting> getSetting(type: Class<T>): T {
        synchronized(settingsMap) {
            @Suppress("UNCHECKED_CAST")
            return settingsMap[type] as T
        }
    }

    fun <T : Setting> updateSetting(updatedSetting: T) {
        val currentList = _settingsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it::class == updatedSetting::class }
        if (index != -1) {
            currentList[index] = updatedSetting
            _settingsFlow.value = currentList
            settingsMap[updatedSetting::class.java] = updatedSetting
            updatedSetting.save()
        }
    }

    init {
        val newSettings = arrayListOf(
            Locale(),
            OperationSound(),
            Light(),
            PowerSavingMode(),
            TimeAdjustment(appContext),
            KeepAlive(appContext),
        )
        updateSettingsAndMap(filter(newSettings))

        val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
        CoroutineScope(coroutineContext).launch {
            val settingsJson = Gson().toJsonTree(api.getSettings()).asJsonObject
            val appSettingsJson = Gson().toJsonTree(appSettings).asJsonObject

            // Merge appSettingsJson into settingsJson
            for (entry in appSettingsJson.entrySet()) {
                settingsJson.add(entry.key, entry.value)
            }

            // Serialize the merged JsonObject
            val settingStr = Gson().toJson(settingsJson)

            updateSettingsAndMap(fromJson(settingStr))
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
        val setting = settingsMap[KeepAlive::class.java] as KeepAlive
        setting.keepAlive = value == true
        updatedObjects.add(setting)
    }

    private fun handlePowerSavingMode(value: Any, updatedObjects: MutableSet<Setting>) {
        if (WatchInfo.hasPowerSavingMode) {
            val setting = settingsMap[PowerSavingMode::class.java] as PowerSavingMode
            setting.powerSavingMode = value == true
            updatedObjects.add(setting)
        }
    }

    private fun handleTimeAdjustment(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[TimeAdjustment::class.java] as TimeAdjustment
        setting.timeAdjustment = value == true
        updatedObjects.add(setting)
    }

    private fun handleAdjustmentTimeMinutes(value: Any, updatedObjects: MutableSet<Setting>) {
        if (!WatchInfo.alwaysConnected) {
            val setting = settingsMap[TimeAdjustment::class.java] as TimeAdjustment
            setting.adjustmentTimeMinutes = value as Int
            updatedObjects.add(setting)
        }
    }

    private fun handleButtonTone(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[OperationSound::class.java] as OperationSound
        setting.sound = value == true
        updatedObjects.add(setting)
    }

    private fun handleButtonVibration(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[OperationSound::class.java] as OperationSound
        setting.vibrate = value == true
        updatedObjects.add(setting)
    }

    private fun handleAutoLight(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[Light::class.java] as Light
        setting.autoLight = value == true
        updatedObjects.add(setting)
    }

    private fun handleLightDuration(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[Light::class.java] as Light
        setting.duration = if (value == Light.LightDuration.TWO_SECONDS.value) {
            Light.LightDuration.TWO_SECONDS
        } else {
            Light.LightDuration.FOUR_SECONDS
        }
        updatedObjects.add(setting)
    }

    private fun handleTimeFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[Locale::class.java] as Locale
        setting.timeFormat = if (value == Locale.TimeFormat.TWELVE_HOURS.value) {
            Locale.TimeFormat.TWELVE_HOURS
        } else {
            Locale.TimeFormat.TWENTY_FOUR_HOURS
        }
        updatedObjects.add(setting)
    }

    private fun handleDateFormat(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[Locale::class.java] as Locale
        setting.dateFormat = if (value == Locale.DateFormat.MONTH_DAY.value) {
            Locale.DateFormat.MONTH_DAY
        } else {
            Locale.DateFormat.DAY_MONTH
        }
        updatedObjects.add(setting)
    }

    private fun handleLanguage(value: Any, updatedObjects: MutableSet<Setting>) {
        val setting = settingsMap[Locale::class.java] as Locale
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
                settingsMap[PowerSavingMode::class.java] as PowerSavingMode

            val enablePowerSetting = batteryLevel <= 15 || currentPowerSavingMode.powerSavingMode
            val powerSavings = PowerSavingMode(enablePowerSetting)
            smartSettings.add(powerSavings)
        }

        // Time adjustment
        val notifyMe = LocalDataStorage.getTimeAdjustmentNotification(appContext)
        val timeAdjustment = TimeAdjustment(appContext, true, 30, notifyMe, 0)
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
            }.onFailure { e ->
                ProgressEvents.onNext("Error", e.message ?: "")
            }
        }
    }

    fun sendToWatch() {
        val settings = Settings()

        val localeSetting: Locale = settingsMap[Locale::class.java] as Locale
        settings.language = localeSetting.dayOfWeekLanguage.englishName
        settings.timeFormat = localeSetting.timeFormat.value
        settings.dateFormat = localeSetting.dateFormat.value

        val lightSetting: Light = settingsMap[Light::class.java] as Light
        settings.autoLight = lightSetting.autoLight
        settings.lightDuration = lightSetting.duration.value

        if (WatchInfo.hasPowerSavingMode) {
            val powerSavingMode: PowerSavingMode =
                settingsMap[PowerSavingMode::class.java] as PowerSavingMode
            settings.powerSavingMode = powerSavingMode.powerSavingMode
        }

        if (!WatchInfo.alwaysConnected) { // Auto-time-adjustment does not apply for always-connected watches
            val timeAdjustment: TimeAdjustment =
                settingsMap[TimeAdjustment::class.java] as TimeAdjustment
            settings.timeAdjustment = timeAdjustment.timeAdjustment
            settings.adjustmentTimeMinutes = timeAdjustment.adjustmentTimeMinutes
            LocalDataStorage.setTimeAdjustmentNotification(
                appContext,
                timeAdjustment.timeAdjustmentNotifications
            )
        }

        val buttonTone: OperationSound =
            settingsMap[OperationSound::class.java] as OperationSound
        settings.buttonTone = buttonTone.sound
        settings.keyVibration = buttonTone.vibrate

        viewModelScope.launch {
            runCatching {
                api.setSettings(settings)
                AppSnackbar(appContext.getString(R.string.settings_sent_to_watch))
            }.onFailure { e ->
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }

        }
    }
}
