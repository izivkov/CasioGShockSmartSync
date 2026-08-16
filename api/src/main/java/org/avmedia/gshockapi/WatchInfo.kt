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
    val hasHomeTime:            Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHomeTime
    val hasMultipleFonts:       Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasMultipleFonts
    val hasStepCounter:         Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasStepCounter
    val hasStepCounterMock:     Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasStepCounterMock
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
        val hasStepCounterMock: Boolean = false,
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
            hasStepCounterMock = false,
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
            hasHomeTime = true,
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
            hasTemperature = false, hasBatteryLevel = false,
            worldCities = false, hasHomeTime = false,
            hasStepCounter = true,
            hasDateFormat = false,
            weekLanguageSupported = false,
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
            hasStepCounter = false,
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
            worldCities = false, hasHomeTime = false,
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

    /**
     * Exact model-name mapping from the official Casio app.
     *
     * Matching is deliberately exact: no startsWith()/contains() matching is used.
     * Watches sharing a known Casio module are assigned to the same WatchInfo block.
     * Models for which we do not yet have enough information use GENERIC.
     */
    private val exactModelMap: Map<String, WatchModel> = buildMap {
        // -----------------------------------------------------------------
        // Additional Casio model-index research. Module numbers are the
        // hardware module numbers.
        // Same module number is authoritative: all models sharing a module
        // MUST use the same WatchModel/ModelInfo block. Existing cross-module
        // mappings are retained only as a secondary compatibility rule.
        // GENERIC entries below intentionally remain GENERIC: module identity
        // alone does not prove that their attributes match another WatchModel.
        // -----------------------------------------------------------------
        // Module 3452: GPR-B1000
        put("GPR-B1000", WatchModel.GPR)
        // Module 3459: GMW-B5000, GW-B5000
        // GW-B5000 was explicitly mapped in the original WatchInfo to the
        // same functional WatchModel family as GW-B5600, despite its
        // different module. Preserve that previous mapping.
        put("GMW-B5000", WatchModel.GMW)
        put("GW-B5000", WatchModel.GW)
        // Module 3461: GW-B5600, MRG-B5000
        put("GW-B5600", WatchModel.GW)
        put("MRG-B5000", WatchModel.GW)
        // Module 3464: GBD-800, GMD-B800
        put("GBD-800", WatchModel.GBD_800)
        put("GMD-B800", WatchModel.GBD_800)
        // Module 3475: GBD-H1000
        put("GBD-H1000", WatchModel.GBD)
        // Module 3481: GBD-100
        put("GBD-100", WatchModel.GBD)
        // Module 3482: GBX-100
        put("GBX-100", WatchModel.GBD)
        // Module 3491: GSR-H1000
        put("GSR-H1000", WatchModel.GENERIC)
        // Module 3506: GBD-200
        put("GBD-200", WatchModel.GBD)
        // Module 3509: DW-B5600
        put("DW-B5600", WatchModel.DW_B5600)
        // Module 3515: GBD-H2000, DW-GH5600
        put("GBD-H2000", WatchModel.DW_H5600)
        // Module 3515: DW-GH5600
        put("DW-GH5600", WatchModel.DW_H5600)
        // Module 3516: DW-H5600
        put("DW-H5600", WatchModel.DW_H5600)
        // Module 3539: GMW-B5000#, GW-B5600#, MRG-B5000#, TRN-50, GCW-B5000, PRJ-BW002
        put("GMW-B5000#", WatchModel.GCW_B5000)
        put("GW-B5600#", WatchModel.GCW_B5000)
        put("MRG-B5000#", WatchModel.GCW_B5000)
        put("TRN-50", WatchModel.GCW_B5000)
        put("GCW-B5000", WatchModel.GCW_B5000)
        put("PRJ-BW002", WatchModel.GCW_B5000)
        // Module 3552: GD-B500
        // Module 3520: GD-B500
        put("GD-B500", WatchModel.GENERIC)
        // Module 3554: GPR-H1000
        put("GPR-H1000", WatchModel.GPR)
        // Module 3565: ABL-100WE
        put("ABL-100WE", WatchModel.ABL_100)
        // Module 3568: GBD-300
        put("GBD-300", WatchModel.GBD)
        // Module 3575: GMW-BZ5000
        put("GMW-BZ5000", WatchModel.GMW_BZ5000)
        // Module 3577: GM-H5600
        put("GM-H5600", WatchModel.DW_H5600)
        // Module 3586: GBX-H5600
        put("GBX-H5600", WatchModel.GBD)
        // Module 3587: GDG-B100
        put("GDG-B100", WatchModel.GBD)
        // Module 3599: GWF-300
        put("GWF-300", WatchModel.GENERIC)
        // Module 5537: ECB-800
        put("ECB-800", WatchModel.ECB)
        // Module 5554: GBA-800
        put("GBA-800", WatchModel.GA)
        // Module 5582: ECB-900, ECB-950, GST-B200, GST-B300
        put("ECB-900", WatchModel.GST)
        put("ECB-950", WatchModel.GST)
        put("GST-B200", WatchModel.GST)
        put("GST-B300", WatchModel.GST)
        // Module 5588: GWR-B1000
        put("GWR-B1000", WatchModel.GW)
        // Module 5594: GMC-B100
        put("GMC-B100", WatchModel.GENERIC)
        // Module 5597: OCW-B1300
        put("OCW-B1300", WatchModel.GENERIC)
        // Module 5602: PRT-B70
        put("PRT-B70", WatchModel.GENERIC)
        // Module 5603: OCW-S5000
        put("OCW-S5000", WatchModel.GENERIC)
        // Module 5604: EQB-1000
        put("EQB-1000", WatchModel.EQB)
        // Module 5618: ECB-10
        put("ECB-10", WatchModel.ECB)
        // Module 5623: GWF-A1000
        put("GWF-A1000", WatchModel.GENERIC)
        // Module 5624: OCW-P2000
        put("OCW-P2000", WatchModel.GENERIC)
        // Module 5636: MTG-B2000, MRG-BF1000
        put("MTG-B2000", WatchModel.GENERIC)
        put("MRG-BF1000", WatchModel.GENERIC)
        // Module 5641: GBA-900
        put("GBA-900", WatchModel.GA)
        // Module 5657: GST-B400
        put("GST-B400", WatchModel.GST)
        // Module 5672: MTG-B3000
        put("MTG-B3000", WatchModel.MTG_B3000)
        // Module 5701: OCW-S7000
        put("OCW-S7000", WatchModel.GENERIC)
        // Module 5713: GWG-B1000
        put("GWG-B1000", WatchModel.GW)
        // Module 5728: OCW-S400
        put("OCW-S400", WatchModel.GENERIC)
        // Module 5736: GA-B010
        put("GA-B010", WatchModel.GA)
        // Module 5737: GBA-950
        // Module 5725: GBA-950
        put("GBA-950", WatchModel.GA)
        // Module 5744: GG-B100X
        put("GG-B100X", WatchModel.GENERIC)
        // Module 5748: GST-B1000, EQB-1300
        // Module 5631: GST-B1000
        put("GST-B1000", WatchModel.GST)
        // Module 5712: EQB-1300
        put("EQB-1300", WatchModel.EQB)
        // Module 5756: GWR-B3000
        // Module 5775: GWR-B3000
        put("GWR-B3000", WatchModel.GW)

        // Official-app models whose module number
        put("GB-5600A", WatchModel.GA)
        put("GB-6900A", WatchModel.GA)
        put("GB-5600B", WatchModel.GA)
        put("GB-6900B", WatchModel.GA)
        put("GB-X6900B", WatchModel.GA)
        put("GBA-400", WatchModel.GA)
        put("GA-B2100", WatchModel.GA)
        put("GM-B2100", WatchModel.GA)
        // Module 5690: GBM-2100
        put("GBM-2100", WatchModel.GA)
        // Module 5688: GA-B001
        put("GA-B001", WatchModel.GA)
        put("GST-B100", WatchModel.GST)
        put("GST-B500", WatchModel.GST)
        // Module 3523: GST-B600
        put("GST-B600", WatchModel.GST)
        // Module 5444: GST-W1000
        put("GST-W1000", WatchModel.GST)
        put("MSG-B100", WatchModel.MSG)
        put("G-B001", WatchModel.GB001)
        put("EQB-500", WatchModel.EQB)
        put("EQB-510", WatchModel.EQB)
        put("EQB-600", WatchModel.EQB)
        put("EQB-700", WatchModel.EQB)
        put("EQB-501", WatchModel.EQB)
        put("EQB-800", WatchModel.EQB)
        put("EQB-900", WatchModel.EQB)
        put("EQB-1100", WatchModel.EQB)
        put("EQB-1200", WatchModel.EQB)
        put("EQB-2000", WatchModel.EQB)
        put("ECB-500", WatchModel.ECB)
        put("ECB-20", WatchModel.ECB)
        put("ECB-30", WatchModel.ECB)
        put("ECB-40", WatchModel.ECB)
        put("ECB-S100", WatchModel.ECB)
        // Module 5638: ECB-2000
        put("ECB-2000", WatchModel.ECB)
        // Module 5708: ECB-2300
        put("ECB-2300", WatchModel.ECB)
        // Module 5688: ECB-2200
        put("ECB-2200", WatchModel.ECB)
        // Module 5682: ECB-S10
        put("ECB-S10", WatchModel.ECB)
        put("GW-BX5600", WatchModel.GW_BX5600)
        put("MTG-B1000", WatchModel.MTG_B1000)
        put("STB-1000", WatchModel.GENERIC)
        put("SHB-100", WatchModel.GENERIC)
        put("SHB-200", WatchModel.GENERIC)
        put("GPW-2000", WatchModel.GENERIC)
        put("GPW-G2000", WatchModel.GENERIC)
        put("MRG-G2000", WatchModel.GENERIC)
        put("OCW-G2000", WatchModel.GENERIC)
        put("MRG-B1000", WatchModel.GENERIC)
        put("LIW-B1000", WatchModel.GENERIC)
        put("OCW-S4000", WatchModel.GENERIC)
        put("OCW-T3000", WatchModel.GENERIC)
        put("OCW-T4000", WatchModel.GENERIC)
        put("OCW-T6000", WatchModel.GENERIC)
        put("OCW-T4000A", WatchModel.GENERIC)
        put("OCW-T4000B", WatchModel.GENERIC)
        put("OCW-T4000C", WatchModel.GENERIC)
        put("GR-B300", WatchModel.GENERIC)
        // Module 5718: MRG-B2100
        put("MRG-B2100", WatchModel.GA)
        // Module 5691: GMC-B2100
        put("GMC-B2100", WatchModel.GA)
        put("OCW-SG1000", WatchModel.GENERIC)
        put("MTG-B4000", WatchModel.GENERIC)
        put("BSA-B100", WatchModel.GENERIC)
        put("GMA-B800", WatchModel.GENERIC)
        put("GR-B100", WatchModel.GENERIC)
        put("GG-B100", WatchModel.GENERIC)
        put("PRT-B50", WatchModel.GENERIC)
        put("GR-B200", WatchModel.GENERIC)
        put("OCW-T200", WatchModel.GENERIC)
        put("OCW-B1200", WatchModel.GENERIC)
        put("OCW-S6000", WatchModel.GENERIC)
        put("OCW-T5000", WatchModel.GENERIC)
        put("OCW-B1400", WatchModel.GENERIC)
        put("MRG-B2000", WatchModel.GENERIC)
        // Module 3559: PRJ-B001
        put("PRJ-B001", WatchModel.GB001)
        put("OCW-5700", WatchModel.GENERIC)
        // Module 5734: MTG-B3100
        put("MTG-B3100", WatchModel.MTG_B3000)
        put("OCW-5800", WatchModel.GENERIC)
        // Module 5699: PRW-B1000
        put("PRW-B1000", WatchModel.GENERIC)
        // Module 3517: GMD-B300
        put("GMD-B300", WatchModel.GENERIC)
        // Module 3564: WS-B1000
        put("WS-B1000", WatchModel.GENERIC)
        // Module 3563: F-B100W
        put("F-B100W", WatchModel.GENERIC)
        // Module 5750: OCW-P3000
        put("OCW-P3000", WatchModel.GENERIC)
    }

    /** Pure: derive the short name from the Bluetooth device name. */
    private fun deriveShortName(name: String): String =
        name.removePrefix("CASIO ").trim().split(" ").firstOrNull().orEmpty()

    /** Pure: resolve only an exact official model name; never use partial matching. */
    private fun resolveModel(name: String): WatchModel {
        val modelName = name.removePrefix("CASIO ").trim()
        return exactModelMap[modelName] ?: WatchModel.GENERIC
    }
    /** Pure: look up ModelInfo, falling back to GENERIC. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveModelInfo(model: WatchModel): ModelInfo =
        modelMap[model] ?: modelMap.getValue(WatchModel.GENERIC)

    /** Pure: build a complete new State from a device name. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildState(name: String): State {
        val shortName = deriveShortName(name)
        val model     = resolveModel(name)
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
