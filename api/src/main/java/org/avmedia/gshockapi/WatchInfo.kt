package org.avmedia.gshockapi

/**
 * This class keeps information about the characteristics of the currently connected watch. Based on
 * that, the application can display different information.
 */
data object WatchInfo {

    // =========================================================================
    // Immutable State
    // =========================================================================

    private data class State(
        val name: String = "",
        val shortName: String = "",
        val address: String = "",
        val model: WatchModel = WatchModel.GENERIC,
        val info: ModelInfo = ModelInfo(model = WatchModel.GENERIC)
    )

    private var state = State()

    // =========================================================================
    // Public read-only accessors
    // =========================================================================

    val name:                   String  get() = state.name
    val shortName:              String  get() = state.shortName
    val model:                  WatchModel get() = state.model
    val worldCitiesCount:       Int     get() = state.info.worldCitiesCount
    val dstCount:               Int     get() = state.info.dstCount
    val alarmCount:             Int     get() = state.info.alarmCount
    val hasAutoLight:           Boolean get() = state.info.hasAutoLight
    val hasReminders:           Boolean get() = state.info.hasReminders
    val shortLightDuration:     String  get() = state.info.shortLightDuration
    val longLightDuration:      String  get() = state.info.longLightDuration
    val weekLanguageSupported:  Boolean get() = state.info.weekLanguageSupported
    val worldCities:            Boolean get() = state.info.worldCities
    val hasTemperature:         Boolean get() = state.info.hasTemperature
    val hasBatteryLevel:        Boolean get() = state.info.hasBatteryLevel
    val batteryLevelLowerLimit: Int     get() = state.info.batteryLevelLowerLimit
    val batteryLevelUpperLimit: Int     get() = state.info.batteryLevelUpperLimit
    val alwaysConnected:        Boolean get() = state.info.alwaysConnected
    val findButtonUserDefined:  Boolean get() = state.info.findButtonUserDefined
    val hasPowerSavingMode:     Boolean get() = state.info.hasPowerSavingMode
    val chimeInSettings:        Boolean get() = state.info.chimeInSettings
    val vibrate:                Boolean get() = state.info.vibrate
    val hasHealthFunctions:     Boolean get() = state.info.hasHealthFunctions
    val hasMessages:            Boolean get() = state.info.hasMessages
    val hasDateFormat:          Boolean get() = state.info.hasDateFormat
    val hasWorldCities:         Boolean get() = state.info.hasWorldCities
    val hasHomeTime:            Boolean get() = state.info.hasHomeTime
    val hasMultipleFonts:       Boolean get() = state.info.hasMultipleFonts
    val hasStepCounter:         Boolean get() = state.info.hasStepCounter
    val hasNewTimeFormat:       Boolean get() = state.info.hasNewTimeFormat
    val hasSecondDial:          Boolean get() = state.info.hasSecondDial

    // =========================================================================
    // Domain Types
    // =========================================================================

    enum class WatchModel {
        GA, GW, DW_B5600, DW, GMW, GPR, GST, MSG, GB001, GBD, GBD_800,
        MRG_B5000, GCW_B5000, EQB, ECB, ABL_100, DW_H5600, GMW_BZ5000,
        GW_BX5600, MTG_B1000, GENERIC,
    }

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
        val batteryLevelLowerLimit: Int = 15,
        val batteryLevelUpperLimit: Int = 20,
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
        val hasSecondDial: Boolean = false,
    )

    // =========================================================================
    // Pure Functional Core
    // =========================================================================

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
            worldCitiesCount = 2, dstCount = 3,
            hasAutoLight = true, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasMultipleFonts = true,
            hasNewTimeFormat = true,
        ),
        ModelInfo(
            model = WatchModel.MTG_B1000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasSecondDial = true,
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

    private val modelMap: Map<WatchModel, ModelInfo> = modelList.associateBy { it.model }

    /** Pure: derive short name from full device name. */
    private fun deriveShortName(name: String): String =
        name.split(" ").getOrElse(1) { "" }

    /** Pure: map short name prefix to WatchModel. */
    private fun resolveModel(shortName: String): WatchModel = when {
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
    private fun resolveModelInfo(model: WatchModel): ModelInfo =
        modelMap[model] ?: modelMap.getValue(WatchModel.GENERIC)

    /** Pure: build a complete new State from a device name. */
    private fun buildState(name: String): State {
        val shortName = deriveShortName(name)
        val model     = resolveModel(shortName)
        val info      = resolveModelInfo(model)
        return State(name = name, shortName = shortName, model = model, info = info)
    }

    // =========================================================================
    // Imperative Shell: state mutations + side effects
    // =========================================================================

    fun setNameAndModel(name: String) {
        state = buildState(name)
        ProgressEvents.onNext("DeviceName", state.name)
    }

    fun setAddress(address: String) {
        state = state.copy(address = address)
        ProgressEvents.onNext("DeviceAddress", address)
    }

    fun getAddress(): String = state.address

    fun reset() {
        state = State()
    }
}
