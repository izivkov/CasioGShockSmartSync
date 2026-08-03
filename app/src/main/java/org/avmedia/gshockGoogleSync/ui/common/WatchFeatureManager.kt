package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.runtime.mutableIntStateOf
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface IWatchFeatureManager {
    fun isFeatureSupported(featureId: String): Boolean
    fun isCardSupported(cardId: String): Boolean
    fun getString(featureId: String): String?
    fun getWatchName(): String
    fun getAlarmCount(): Int
}

@Singleton
class WatchFeatureManager @Inject constructor() : IWatchFeatureManager {

    private val refreshCounter = mutableIntStateOf(0)

    init {
        ProgressEvents.runEventActions("WatchFeatureManager", arrayOf(
            EventAction("DeviceName") {
                refreshCounter.intValue++
            },
            EventAction("ConnectionSetupComplete") {
                refreshCounter.intValue++
            }
        ))
    }

    // To support a new WatchInfo feature: add one line here.
    private val featureMap = mapOf(
        "locale.date_format" to { WatchInfo.hasDateFormat },
        "locale.time_format" to { WatchInfo.hasTimeFormat },
        "locale.week_language" to { WatchInfo.weekLanguageSupported },
        "settings.power_saving" to { WatchInfo.hasPowerSavingMode },
        "settings.multiple_fonts" to { WatchInfo.hasMultipleFonts },
        "light.auto_light" to { WatchInfo.hasAutoLight },
        "light.duration" to { true },
        "operation_tone.sound" to { true },
        "operation_tone.vibrate" to { WatchInfo.vibrate },
        "time.battery" to { WatchInfo.hasBatteryLevel },
        "time.world_cities" to { WatchInfo.worldCities },
        "time.home_time" to { WatchInfo.hasHomeTime },
        "time.temperature" to { WatchInfo.hasTemperature },
        "actions.find_phone" to { WatchInfo.findButtonUserDefined },
        "actions.reminders" to { WatchInfo.hasReminders },
        "alarms.chime" to { WatchInfo.chimeInSettings },
        "time_adjustment.always_connected" to { WatchInfo.alwaysConnected }
    )

    // To support a new WatchInfo string field: add one line here.
    private val stringMap = mapOf(
        "light.short_duration" to { WatchInfo.shortLightDuration },
        "light.long_duration" to { WatchInfo.longLightDuration }
    )

    // To group features under one card: add/extend one line here.
    // A cardId with no entry (or an empty list) falls back to "always supported" —
    // see isCardSupported below.
    private val cardGroups = mapOf(
        "locale_card" to listOf("locale.date_format", "locale.time_format", "locale.week_language"),
        "power_saving_card" to listOf("settings.power_saving"),
        "font_card" to listOf("settings.multiple_fonts"),
        "light_card" to listOf("light.auto_light", "light.duration"),
        "operation_tone_card" to listOf("operation_tone.sound", "operation_tone.vibrate"),
        "time_adjustment_card" to listOf("time_adjustment.always_connected")
    )

    override fun isFeatureSupported(featureId: String): Boolean {
        refreshCounter.intValue // Observe for recomposition
        val lookup = featureMap[featureId]
        if (lookup == null) {
            Timber.w("isFeatureSupported: unknown featureId '$featureId' — defaulting to supported=true")
        }
        val supported = lookup?.invoke() ?: true
        Timber.d("isFeatureSupported: id=$featureId, supported=$supported (Watch: ${WatchInfo.model})")
        return supported
    }

    override fun isCardSupported(cardId: String): Boolean {
        refreshCounter.intValue // Observe for recomposition
        val features = cardGroups[cardId]
        if (features == null) {
            Timber.w("isCardSupported: unknown cardId '$cardId' — defaulting to supported=true")
            return true
        }
        val supported = features.any { isFeatureSupported(it) }
        Timber.d("isCardSupported: id=$cardId, supported=$supported")
        return supported
    }

    override fun getString(featureId: String): String? {
        refreshCounter.intValue // Observe for recomposition
        val lookup = stringMap[featureId]
        if (lookup == null) {
            Timber.w("getString: unknown featureId '$featureId'")
        }
        return lookup?.invoke()
    }

    override fun getWatchName(): String {
        refreshCounter.intValue // Observe for recomposition
        return WatchInfo.name
    }

    override fun getAlarmCount(): Int {
        refreshCounter.intValue // Observe for recomposition
        return WatchInfo.alarmCount
    }
}
