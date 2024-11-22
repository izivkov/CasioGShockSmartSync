package org.avmedia.gshockGoogleSync.ui.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.MainActivity.Companion.applicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.WatchInfo
import org.json.JSONObject
import java.text.SimpleDateFormat
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: GShockRepository
) : ViewModel() {
    abstract class Setting(open var title: String) {
        open fun save() {}
    }

    private val _settings = MutableStateFlow<ArrayList<Setting>>(arrayListOf())
    val settings: StateFlow<ArrayList<Setting>> = _settings
    private val settingsMap: MutableMap<Class<out Setting>, Setting> =
        _settings.value.associateBy { it::class.java }.toMutableMap()

    private fun updateSettingsAndMap(newSettings: ArrayList<Setting>) {
        settingsMap.clear()
        _settings.value = arrayListOf()

        newSettings.forEach { setting ->
            _settings.value.add(setting)
            settingsMap[setting::class.java] = setting
        }
    }

    fun <T : Setting> getSetting(type: Class<T>): T {
        println("getSetting: $type, settingsMap size: ${settingsMap.size}")
        return settingsMap[type] as T
    }

    fun <T : Setting> updateSetting(updatedSetting: T) {
        val currentList = _settings.value
        val index = currentList.indexOfFirst { it::class == updatedSetting::class }
        if (index != -1) {
            currentList[index] = updatedSetting
            updateSettingsAndMap(currentList)
            updatedSetting.save()
        }
    }

    data class Locale(
        var timeFormat: TIME_FORMAT = TIME_FORMAT.TWELVE_HOURS,
        var dateFormat: DATE_FORMAT = DATE_FORMAT.MONTH_DAY,
        var dayOfWeekLanguage: DAY_OF_WEEK_LANGUAGE = DAY_OF_WEEK_LANGUAGE.ENGLISH,
    ) : Setting("Locale") {
        enum class TIME_FORMAT(val value: String) {
            TWELVE_HOURS("12h"), TWENTY_FOUR_HOURS("24h"),
        }

        enum class DATE_FORMAT(val value: String) {
            MONTH_DAY("MM:DD"), DAY_MONTH("DD:MM"),
        }

        enum class DAY_OF_WEEK_LANGUAGE(var value: String) {
            ENGLISH("English"), SPANISH("Spanish"), FRENCH("French"), GERMAN("German"), ITALIAN("Italian"), RUSSIAN(
                "Russian"
            )
        }
    }

    data class OperationSound(var sound: Boolean = true) : Setting("Button Sound")

    data class Light(
        var autoLight: Boolean = false,
        var duration: LIGHT_DURATION = LIGHT_DURATION.TWO_SECONDS,
    ) : Setting("Light") {
        enum class LIGHT_DURATION(val value: String) {
            TWO_SECONDS("2s"), FOUR_SECONDS("4s")
        }
    }

    data class PowerSavingMode(var powerSavingMode: Boolean = false) :
        Setting("Power Saving Mode")

    data class TimeAdjustment(
        var timeAdjustment: Boolean = true,
        var adjustmentTimeMinutes: Int = 0,
        var timeAdjustmentNotifications: Boolean =
            LocalDataStorage.getTimeAdjustmentNotification(applicationContext()),
        var fineAdjustment: Int = LocalDataStorage.getFineTimeAdjustment(applicationContext()),
    ) : Setting("Time Adjustment") {
        override fun save() {
            LocalDataStorage.setTimeAdjustmentNotification(
                applicationContext(),
                timeAdjustmentNotifications
            )
            LocalDataStorage.setFineTimeAdjustment(
                applicationContext(),
                fineAdjustment
            )
        }
    }

    data class DnD(
        var dnd: Boolean = true,
    ) : Setting("DnD")

    init {
        val newSettings = arrayListOf(
            Locale(),
            OperationSound(),
            Light(),
            PowerSavingMode(),
            TimeAdjustment(),
        )
        updateSettingsAndMap(filter(newSettings))

        val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()
        CoroutineScope(coroutineContext).launch {
            val settingStr = Gson().toJson(repository.getSettings())
            updateSettingsAndMap(fromJson(settingStr))
        }
    }

    private fun filter(settings: ArrayList<Setting>): ArrayList<Setting> {
        return settings.filter { setting ->
            when (setting) {
                is PowerSavingMode -> WatchInfo.hasPowerSavingMode
                is TimeAdjustment -> !WatchInfo.alwaysConnected
                else -> true
            }
        } as ArrayList<Setting>
    }

    @Synchronized
    fun fromJson(jsonStr: String): ArrayList<Setting> {
        /*
        jsonStr:
        {"adjustmentTimeMinutes":23, "autoLight":true,"dateFormat":"MM:DD",
        "language":"Spanish","lightDuration":"4s","powerSavingMode":true,
        "timeAdjustment":true, "timeFormat":"12h","timeTone":false}
        */

        // Create a Set to store updated objects and avoid duplicates
        val updatedObjects = mutableSetOf<Setting>()

        val jsonObj = JSONObject(jsonStr)
        val keys = jsonObj.keys()

        while (keys.hasNext()) {
            val key: String = keys.next()
            val value = jsonObj.get(key)
            when (key) {
                "powerSavingMode" -> {
                    if (WatchInfo.hasPowerSavingMode) {
                        val setting: PowerSavingMode =
                            settingsMap[PowerSavingMode::class.java] as PowerSavingMode
                        setting.powerSavingMode = value == true
                        updatedObjects.add(setting)
                    }
                }

                "timeAdjustment" -> {
                    if (!WatchInfo.alwaysConnected) {
                        val setting: TimeAdjustment =
                            settingsMap[TimeAdjustment::class.java] as TimeAdjustment
                        setting.timeAdjustment = value == true
                        updatedObjects.add(setting)
                    }
                }

                "adjustmentTimeMinutes" -> {
                    if (!WatchInfo.alwaysConnected) {
                        val setting: TimeAdjustment =
                            settingsMap[TimeAdjustment::class.java] as TimeAdjustment
                        setting.adjustmentTimeMinutes = value as Int
                        updatedObjects.add(setting)
                    }
                }

                "buttonTone" -> {
                    val setting: OperationSound =
                        settingsMap[OperationSound::class.java] as OperationSound
                    setting.sound = value == true
                    updatedObjects.add(setting)
                }

                "autoLight" -> {
                    val setting: Light = settingsMap[Light::class.java] as Light
                    setting.autoLight = value == true
                    updatedObjects.add(setting)
                }

                "lightDuration" -> {
                    val setting: Light = settingsMap[Light::class.java] as Light
                    if (value == Light.LIGHT_DURATION.TWO_SECONDS.value) {
                        setting.duration = Light.LIGHT_DURATION.TWO_SECONDS
                    } else {
                        setting.duration = Light.LIGHT_DURATION.FOUR_SECONDS
                    }
                    updatedObjects.add(setting)
                }

                "timeFormat" -> {
                    val setting: Locale = settingsMap[Locale::class.java] as Locale
                    if (value == Locale.TIME_FORMAT.TWELVE_HOURS.value) {
                        setting.timeFormat = Locale.TIME_FORMAT.TWELVE_HOURS
                    } else {
                        setting.timeFormat = Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
                    }
                    updatedObjects.add(setting)
                }

                "dateFormat" -> {
                    val setting: Locale = settingsMap[Locale::class.java] as Locale
                    if (value == Locale.DATE_FORMAT.MONTH_DAY.value) {
                        setting.dateFormat = Locale.DATE_FORMAT.MONTH_DAY
                    } else {
                        setting.dateFormat = Locale.DATE_FORMAT.DAY_MONTH
                    }
                    updatedObjects.add(setting)
                }

                "language" -> {
                    val setting: Locale = settingsMap[Locale::class.java] as Locale
                    when (value) {
                        Locale.DAY_OF_WEEK_LANGUAGE.ENGLISH.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.ENGLISH

                        Locale.DAY_OF_WEEK_LANGUAGE.SPANISH.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.SPANISH

                        Locale.DAY_OF_WEEK_LANGUAGE.FRENCH.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.FRENCH

                        Locale.DAY_OF_WEEK_LANGUAGE.GERMAN.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.GERMAN

                        Locale.DAY_OF_WEEK_LANGUAGE.ITALIAN.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.ITALIAN

                        Locale.DAY_OF_WEEK_LANGUAGE.RUSSIAN.value -> setting.dayOfWeekLanguage =
                            Locale.DAY_OF_WEEK_LANGUAGE.RUSSIAN
                    }
                    updatedObjects.add(setting)
                }
            }
        }

        // Return the updated objects as an ArrayList
        return ArrayList(updatedObjects)
    }

    @SuppressLint("SimpleDateFormat")
    private suspend fun getSmartDefaults(
    ): ArrayList<Setting> {
        val smartSettings = arrayListOf<Setting>()
        val currentLocale = java.util.Locale.getDefault()

        // Locale
        val language = when (currentLocale.language) {
            "en" -> Locale.DAY_OF_WEEK_LANGUAGE.ENGLISH
            "es" -> Locale.DAY_OF_WEEK_LANGUAGE.SPANISH
            "fr" -> Locale.DAY_OF_WEEK_LANGUAGE.FRENCH
            "de" -> Locale.DAY_OF_WEEK_LANGUAGE.GERMAN
            "it" -> Locale.DAY_OF_WEEK_LANGUAGE.ITALIAN
            "ru" -> Locale.DAY_OF_WEEK_LANGUAGE.RUSSIAN
            else -> Locale.DAY_OF_WEEK_LANGUAGE.ENGLISH
        }

        val dateTimePattern = SimpleDateFormat().toPattern()
        val datePattern = dateTimePattern.split(" ")[0]
        val timePattern = dateTimePattern.split(" ")[1]

        val dateFormat = if (datePattern.lowercase().startsWith("d")) {
            Locale.DATE_FORMAT.DAY_MONTH
        } else {
            Locale.DATE_FORMAT.MONTH_DAY
        }
        val timeFormat = if (timePattern[0] == 'h') {
            Locale.TIME_FORMAT.TWELVE_HOURS
        } else {
            Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
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
            applicationContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val buttonTone =
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val operationSound = OperationSound(buttonTone)
        smartSettings.add(operationSound)

        // Light settings
        val autoLight = false
        val light = Light(autoLight, Light.LIGHT_DURATION.TWO_SECONDS)
        smartSettings.add(light)

        // Power Save Mode
        val batteryLevel = repository.getBatteryLevel()
        val powerSavingMode = batteryLevel <= 15
        val powerSavings = PowerSavingMode(powerSavingMode)
        smartSettings.add(powerSavings)

        // Time adjustment
        val timeAdjustment = TimeAdjustment(true, 30, false, 0)
        smartSettings.add(timeAdjustment)

        return smartSettings
    }

    fun setSmartDefaults() {
        viewModelScope.launch {
            try {
                updateSettingsAndMap(getSmartDefaults())
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }
        }
    }

    fun sendToWatch() {
        val settings = Settings()

        val localeSetting: Locale = settingsMap[Locale::class.java] as Locale
        settings.language = localeSetting.dayOfWeekLanguage.value
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
                applicationContext(),
                timeAdjustment.timeAdjustmentNotifications
            )
        }

        val buttonTone: OperationSound =
            settingsMap[OperationSound::class.java] as OperationSound
        settings.buttonTone = buttonTone.sound

        viewModelScope.launch {
            try {
                repository.setSettings(settings)
                AppSnackbar("Settings Sent to Watch")
            } catch (e: Exception) {
                ProgressEvents.onNext("ApiError", e.message ?: "")
            }
        }
    }
}
