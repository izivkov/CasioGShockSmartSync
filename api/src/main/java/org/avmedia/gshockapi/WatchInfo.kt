package org.avmedia.gshockapi

import android.os.Build
import androidx.annotation.RequiresApi
import org.avmedia.gshockapi.protocols.AnalogueProtocol
import org.avmedia.gshockapi.protocols.MipProtocol
import org.avmedia.gshockapi.protocols.StandardProtocol
import org.avmedia.gshockapi.protocols.WatchProtocol

/**
 * This class keeps information about the characteristics of the currently connected watch. Based on
 * that, the application can display different information.
 */
data object WatchInfo {

    // =========================================================================
    // Immutable State
    // =========================================================================

    @RequiresApi(Build.VERSION_CODES.O)
    private data class State(
        val name: String = "",
        val shortName: String = "",
        val address: String = "",
        val model: WatchModel = WatchModel.GENERIC,
        val info: ModelInfo = ModelInfo(model = WatchModel.GENERIC)
    )

    private var state: Any = "" // placeholder for late init

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getState(): State {
        if (state !is State) {
            state = State()
        }
        return state as State
    }

    // =========================================================================
    // Public read-only accessors
    // =========================================================================

    val name:                   String  @RequiresApi(Build.VERSION_CODES.O) get() = getState().name
    val shortName:              String  @RequiresApi(Build.VERSION_CODES.O) get() = getState().shortName
    val model:                  WatchModel @RequiresApi(Build.VERSION_CODES.O) get() = getState().model
    val worldCitiesCount:       Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.worldCitiesCount
    val dstCount:               Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.dstCount
    val alarmCount:             Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.alarmCount
    val hasAutoLight:           Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasAutoLight
    val hasReminders:           Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasReminders
    val shortLightDuration:     String  @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.shortLightDuration
    val longLightDuration:      String  @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.longLightDuration
    val weekLanguageSupported:  Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.weekLanguageSupported
    val worldCities:            Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.worldCities
    val hasTemperature:         Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTemperature
    val hasBatteryLevel:        Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasBatteryLevel
    val batteryLevelLowerLimit: Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.batteryLevelLowerLimit
    val batteryLevelUpperLimit: Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.batteryLevelUpperLimit
    val alwaysConnected:        Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.alwaysConnected
    val findButtonUserDefined:  Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.findButtonUserDefined
    val hasPowerSavingMode:     Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasPowerSavingMode
    val chimeInSettings:        Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.chimeInSettings
    val vibrate:                Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.vibrate
    val hasHealthFunctions:     Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHealthFunctions
    val hasMessages:            Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasMessages
    val hasDateFormat:          Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasDateFormat
    val hasWorldCities:         Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasWorldCities
    val hasHomeTime:            Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHomeTime
    val hasMultipleFonts:       Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasMultipleFonts
    val hasStepCounter:         Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasStepCounter
    val hasNewTimeFormat:       Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasNewTimeFormat
    val hasTimeAdjustment:      Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTimeAdjustment
    val hasSecondDial:          Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasSecondDial
    val hasFineWatchCondition:  Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasFineWatchCondition
    val hasTimeFormat:          Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTimeFormat
    val hasHourlyChime:         Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHourlyChime
    val hasLongTimerKey:        Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasLongTimerKey
    val settingsSize:           Int     @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.settingsSize
    
    val protocol: WatchProtocol @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.protocol

    // =========================================================================
    // Domain Types
    // =========================================================================

    enum class WatchModel {
        GA, GW, DW_B5600, DW, GMW, GPR, GST, MSG, GB001, GBD, GBD_800,
        MRG_B5000, GCW_B5000, EQB, ECB, ABL_100, DW_H5600, GMW_BZ5000,
        GW_BX5600, MTG_B1000, MTG_B3000, GENERIC,
    }

    @RequiresApi(Build.VERSION_CODES.O)
    data class ModelInfo(
        val model: WatchModel,
        val worldCitiesCount: Int = 2,
        val dstCount: Int = 1,
        val alarmCount: Int = 5,
        val hasAutoLight: Boolean = false,
        val hasReminders: Boolean = false,
        val shortLightDuration: String = "1.5s",
        val longLightDuration: String = "3s",
        val weekLanguageSupported: Boolean = true,
        val worldCities: Boolean = true,
        val hasBatteryLevel: Boolean = true,
        val hasTemperature: Boolean = true,
        val batteryLevelLowerLimit: Int = 9,
        val batteryLevelUpperLimit: Int = 19,
        val alwaysConnected: Boolean = false,
        val findButtonUserDefined: Boolean = false,
        val hasPowerSavingMode: Boolean = true,
        val chimeInSettings: Boolean = false,
        val vibrate: Boolean = false,
        val hasHealthFunctions: Boolean = false,
        val hasMessages: Boolean = false,
        val hasDateFormat: Boolean = true,
        val hasWorldCities: Boolean = true,
        val hasHomeTime: Boolean = true,
        val hasMultipleFonts: Boolean = false,
        val hasStepCounter: Boolean = false,
        val hasNewTimeFormat: Boolean = false,
        val hasTimeAdjustment: Boolean = true,
        val hasSecondDial: Boolean = false,
        val hasFineWatchCondition: Boolean = false,
        val hasTimeFormat: Boolean = true,
        val hasHourlyChime: Boolean = true,
        val hasLongTimerKey: Boolean = false,
        val settingsSize: Int = 17,
        val protocol: WatchProtocol = StandardProtocol
    )

    // =========================================================================
    // Pure Functional Core
    // =========================================================================

    @RequiresApi(Build.VERSION_CODES.O)
    private val modelList = listOf(
        ModelInfo(
            model = WatchModel.GW,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            ),
        ModelInfo(
            model = WatchModel.DW_B5600,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = false, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        ),
        ModelInfo(
            model = WatchModel.GMW_BZ5000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasMultipleFonts = true,
        ),
        ModelInfo(
            model = WatchModel.GW_BX5600,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasMultipleFonts = true,
            hasNewTimeFormat = true,
            protocol = MipProtocol,
        ),
        ModelInfo(
            model = WatchModel.MTG_B1000,
            worldCitiesCount = 6, dstCount = 3, alarmCount = 1,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasSecondDial = true,
            hasFineWatchCondition = true,
            hasHourlyChime = false,
            protocol = AnalogueProtocol,
        ),
        ModelInfo(
            model = WatchModel.MTG_B3000,
            worldCitiesCount = 2, dstCount = 1, alarmCount = 1,
            hasAutoLight = false, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            hasWorldCities = false, hasHomeTime = true,
            hasDateFormat = false, weekLanguageSupported = false,
            hasTimeFormat = false, settingsSize = 12,
            batteryLevelLowerLimit = 0, batteryLevelUpperLimit = 100,
            hasSecondDial = true,
            hasFineWatchCondition = true,
            hasPowerSavingMode = false,
            hasHourlyChime = false,
            hasLongTimerKey = true,
            protocol = AnalogueProtocol,
        ),
        ModelInfo(
            model = WatchModel.MRG_B5000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        ),
        ModelInfo(
            model = WatchModel.GCW_B5000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        ),
        ModelInfo(
            model = WatchModel.GMW,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        ),
        ModelInfo(model = WatchModel.GST,    hasAutoLight = false, hasReminders = true),
        ModelInfo(
            model = WatchModel.ABL_100,
            hasAutoLight = false, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false, hasWorldCities = false,
            hasStepCounter = true,
        ),
        ModelInfo(model = WatchModel.GA,     hasAutoLight = false, hasReminders = true),
        ModelInfo(model = WatchModel.GB001,  hasAutoLight = true,  hasReminders = false),
        ModelInfo(model = WatchModel.MSG,    hasAutoLight = false, hasReminders = true),
        ModelInfo(
            model = WatchModel.GPR,
            hasAutoLight = true, hasReminders = false, weekLanguageSupported = false,
        ),
        ModelInfo(
            model = WatchModel.DW_H5600,
            alarmCount = 4,
            hasAutoLight = true, hasReminders = false,
            vibrate = true, chimeInSettings = true,
            findButtonUserDefined = true,
            shortLightDuration = "1.5s", longLightDuration = "5s",
            hasBatteryLevel = false, alwaysConnected = true, hasDateFormat = false,
            weekLanguageSupported = false,
        ),
        ModelInfo(model = WatchModel.DW,     hasAutoLight = true,  hasReminders = false),
        ModelInfo(
            model = WatchModel.GBD,
            hasAutoLight = true, hasReminders = false,
            worldCities = false, hasTemperature = false,
        ),
        ModelInfo(
            model = WatchModel.GBD_800,
            hasAutoLight = true, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false,
            hasWorldCities = false, hasHomeTime = false,
        ),
        ModelInfo(
            model = WatchModel.EQB,
            hasAutoLight = true, hasReminders = false,
            worldCities = false, hasTemperature = false,
        ),
        ModelInfo(
            model = WatchModel.ECB,
            hasAutoLight = true, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false,
            alwaysConnected = true, findButtonUserDefined = true, hasPowerSavingMode = false,
        ),
        ModelInfo(model = WatchModel.GENERIC),
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private val modelMap: Map<WatchModel, ModelInfo> = modelList.associateBy { it.model }

    /** Pure: derive short name from full device name. */
    private fun deriveShortName(name: String): String =
        name.split(" ").getOrElse(1) { "" }

    /** Pure: map short name prefix to WatchModel. */
    private fun resolveModel(shortName: String): WatchModel = when {
        shortName.startsWith("MTG-B3000")  -> WatchModel.MTG_B3000
        shortName.startsWith("MTG-B1000")  -> WatchModel.MTG_B1000
        shortName.startsWith("MRG-B5000")  -> WatchModel.MRG_B5000
        shortName.startsWith("GCW-B5000")  -> WatchModel.GCW_B5000
        shortName.startsWith("GMW-BZ5000") -> WatchModel.GMW_BZ5000
        shortName.startsWith("GW-BX5600")  -> WatchModel.GW_BX5600
        shortName.startsWith("GM-B2100")   -> WatchModel.GA
        shortName.startsWith("ABL-100")    -> WatchModel.ABL_100
        shortName.startsWith("G-B001")     -> WatchModel.GB001
        shortName.startsWith("GMW")        -> WatchModel.GMW
        shortName.startsWith("GST")        -> WatchModel.GST
        shortName.startsWith("GPR")        -> WatchModel.GPR
        shortName.startsWith("MSG")        -> WatchModel.MSG
        shortName.startsWith("GBD-800")    -> WatchModel.GBD_800
        shortName.startsWith("GBD")        -> WatchModel.GBD
        shortName.startsWith("EQB")        -> WatchModel.EQB
        shortName.startsWith("GMB")        -> WatchModel.GA
        shortName == "ECB-10" || shortName == "ECB-20" || shortName == "ECB-30" -> WatchModel.ECB
        shortName.startsWith("GA")         -> WatchModel.GA
        shortName.startsWith("GB")         -> WatchModel.GA
        shortName.startsWith("GW")         -> WatchModel.GW
        shortName.startsWith("DW-H5600")   -> WatchModel.DW_H5600
        shortName.startsWith("DW-B5600")   -> WatchModel.DW_B5600
        shortName.startsWith("DW")         -> WatchModel.DW
        else                               -> WatchModel.GENERIC
    }

    /** Pure: look up ModelInfo, falling back to GENERIC. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveModelInfo(model: WatchModel): ModelInfo =
        modelMap[model] ?: modelMap.getValue(WatchModel.GENERIC)

    /** Pure: build a complete new State from a device name. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildState(name: String): State {
        val shortName = deriveShortName(name)
        val model     = resolveModel(shortName)
        val info      = resolveModelInfo(model)
        return State(name = name, shortName = shortName, model = model, info = info)
    }

    // =========================================================================
    // Imperative Shell: state mutations + side effects
    // =========================================================================

    @RequiresApi(Build.VERSION_CODES.O)
    fun setNameAndModel(name: String) {
        state = buildState(name)
        ProgressEvents.onNext("DeviceName", (state as State).name)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setAddress(address: String) {
        if (state !is State) {
            state = State(address = address)
        } else {
            state = (state as State).copy(address = address)
        }
        ProgressEvents.onNext("DeviceAddress", address)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getAddress(): String = if (state is State) (state as State).address else ""

    fun reset() {
        state = ""
    }
}
