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

    val name: String @RequiresApi(Build.VERSION_CODES.O) get() = getState().name
    val shortName: String @RequiresApi(Build.VERSION_CODES.O) get() = getState().shortName
    val model: WatchModel @RequiresApi(Build.VERSION_CODES.O) get() = getState().model
    val worldCitiesCount: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.worldCitiesCount
    val dstCount: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.dstCount
    val alarmCount: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.alarmCount
    val hasAutoLight: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasAutoLight
    val hasReminders: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasReminders
    val shortLightDuration: String @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.shortLightDuration
    val longLightDuration: String @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.longLightDuration
    val weekLanguageSupported: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.weekLanguageSupported
    val worldCities: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.worldCities
    val hasTemperature: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTemperature
    val hasBatteryLevel: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasBatteryLevel
    val batteryLevelLowerLimit: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.batteryLevelLowerLimit
    val batteryLevelUpperLimit: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.batteryLevelUpperLimit
    val alwaysConnected: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.alwaysConnected
    val findButtonUserDefined: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.findButtonUserDefined
    val hasPowerSavingMode: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasPowerSavingMode
    val chimeInSettings: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.chimeInSettings
    val vibrate: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.vibrate
    val hasHealthFunctions: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHealthFunctions
    val hasMessages: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasMessages
    val hasDateFormat: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasDateFormat
    val hasWorldCities: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasWorldCities
    val hasHomeTime: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHomeTime
    val hasMultipleFonts: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasMultipleFonts
    val hasStepCounter: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasStepCounter
    val hasNewTimeFormat: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasNewTimeFormat
    val hasTimeAdjustment: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTimeAdjustment
    val hasSecondDial: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasSecondDial
    val hasFineWatchCondition: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasFineWatchCondition
    val hasTimeFormat: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasTimeFormat
    val hasHourlyChime: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasHourlyChime
    val hasLongTimerKey: Boolean @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.hasLongTimerKey
    val settingsSize: Int @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.settingsSize

    val protocol: WatchProtocol @RequiresApi(Build.VERSION_CODES.O) get() = getState().info.protocol

    // =========================================================================
    // Domain Types
    // =========================================================================

    enum class WatchModel {
        GSHOCK_TYPE_A_2KEY, GSHOCK_TYPE_A_3KEY, GSHOCK_TYPE_B_2KEY, GSHOCK_TYPE_B_3KEY,
        GMIX_GBA_400, CASIO_STB_1000, CASIO_EQB_500, CASIO_EQB_510, CASIO_ECB_500,
        SHB_100, SHB_200, EQB_600, EQB_700, EQB_501, EQB_800, EQB_900, GST_B100,
        GPW_2000, GPW_G2000, MRG_G2000, OCW_G2000, MODID_TEST_5501, MRG_B1000,
        LIW_B1000, OCW_S4000, MTG_B1000, OCW_T3000, OCW_T4000, OCW_T6000,
        OCW_T4000A, OCW_T4000B, OCW_T4000C, GR_B300, MRG_B2100, GMC_B2100,
        OCW_SG1000, MTG_B4000, GBA_800, BSA_B100, GBD_800, GMA_B800, GMD_B800,
        GPR_B1000, GMW_B5000, GW_B5600, MRG_B5000, GMW_B5000_SHARP, GW_B5600_SHARP,
        MRG_B5000_SHARP, TRN_50, GCW_B5000, PRJ_BW002, GR_B100, ECB_800, ECB_900,
        ECB_950, GST_B200, GST_B300, GMC_B100, GG_B100, GG_B100X, PRT_B50,
        GR_B200, GWR_B1000, OCW_T200, OCW_S5000, OCW_B1200, OCW_S6000, OCW_T5000,
        OCW_S7000, EQB_1000, EQB_1100, EQB_1200, EQB_2000, OCW_B1300, OCW_B1400,
        OCW_S400, GBD_H1000, GSR_H1000, GBD_100, GBX_100, GBD_200, GBD_H2000,
        DW_H5600, GM_H5600, GPR_H1000, GBD_300, GBX_H5600, GDG_B100, ECB_10,
        ECB_20, ECB_30, ECB_40, GWF_A1000, OCW_P2000, MRG_B2000, MTG_B2000,
        MRG_BF1000, PRT_B70, GST_B400, GST_B500, ECB_S100, MSG_B100, GA_B2100,
        ECB_2000, ECB_2300, GM_B2100, ECB_2200, GST_B600, GBM_2100, PRJ_B001,
        GBA_900, GBA_950, MTG_B3000, OCW_5700, MTG_B3100, OCW_5800, GST_B1000,
        EQB_1300, DW_B5600, GA_B001, G_B001, ECB_S10, GWG_B1000, PRW_B1000,
        GD_B500, ABL_100WE, GMD_B300, WS_B1000, F_B100W, GA_B010, GMW_BZ5000,
        GW_BX5600, GWR_B3000, OCW_P3000, GST_W1000, DW_GH5600, GWF_300, GENERIC
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
    private val modelList = buildList {
        // Module 3461 Template
        val module_3461 = ModelInfo(
            model = WatchModel.GW_B5600,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        )
        // Module 3539 Template
        val module_3539 = ModelInfo(
            model = WatchModel.GMW_B5000_SHARP,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        )
        // Module 3459 Template
        val module_3459 = ModelInfo(
            model = WatchModel.GMW_B5000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        )
        // Module 3509 Template
        val module_3509 = ModelInfo(
            model = WatchModel.DW_B5600,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = false, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
        )
        // Module 3575 Template
        val module_3575 = ModelInfo(
            model = WatchModel.GMW_BZ5000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasMultipleFonts = true,
        )
        // Module 5672 Template
        val module_5672 = ModelInfo(
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
        )
        // Module 5526 Template
        val module_5526 = ModelInfo(
            model = WatchModel.MTG_B1000,
            worldCitiesCount = 6, dstCount = 3, alarmCount = 1,
            hasAutoLight = true, hasReminders = true,
            shortLightDuration = "2s", longLightDuration = "4s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasSecondDial = true,
            hasFineWatchCondition = true,
            hasHourlyChime = false,
            protocol = AnalogueProtocol,
        )
        // Module 3515 Template
        val module_3515 = ModelInfo(
            model = WatchModel.GBD_H2000,
            worldCitiesCount = 6, dstCount = 3,
            hasAutoLight = true, hasReminders = false,
            shortLightDuration = "1.5s", longLightDuration = "3s",
            batteryLevelLowerLimit = 9, batteryLevelUpperLimit = 19,
            hasMultipleFonts = true,
            hasNewTimeFormat = true,
            protocol = MipProtocol,
        )
        // Module 3516 Template
        val module_3516 = ModelInfo(
            model = WatchModel.DW_H5600,
            alarmCount = 4,
            hasAutoLight = true, hasReminders = false,
            vibrate = true, chimeInSettings = true,
            findButtonUserDefined = true,
            shortLightDuration = "1.5s", longLightDuration = "5s",
            hasBatteryLevel = false, alwaysConnected = true, hasDateFormat = false,
            weekLanguageSupported = false,
            hasStepCounter = true,
        )
        // Module 3552 Template
        val module_3552 = ModelInfo(
            model = WatchModel.GD_B500,
            hasAutoLight = false, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false, hasWorldCities = false,
            hasStepCounter = true,
        )
        // Module 3565 Template
        val module_3565 = ModelInfo(
            model = WatchModel.ABL_100WE,
            hasAutoLight = false, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false, hasWorldCities = false,
            hasStepCounter = true,
        )
        // Module 3464 Template
        val module_3464 = ModelInfo(
            model = WatchModel.GBD_800,
            hasAutoLight = true, hasReminders = false,
            hasTemperature = false, hasBatteryLevel = false,
            hasWorldCities = false, hasHomeTime = false,
        )

        // *** Not yet defined:
        // Module 5501 Template
        val module_5501 = ModelInfo(
            model = WatchModel.GPW_2000,
        )
        // Module 5712 Template
        val module_5712 = ModelInfo(
            model = WatchModel.OCW_T6000,
        )
        // Module 5719 Template
        val module_5719 = ModelInfo(
            model = WatchModel.GR_B300,
        )
        // Module 5733 Template
        val module_5733 = ModelInfo(
            model = WatchModel.GMC_B2100,
        )
        // Module 5718 Template
        val module_5718 = ModelInfo(
            model = WatchModel.MRG_B2100,
        )
        // Module 5731 Template
        val module_5731 = ModelInfo(
            model = WatchModel.OCW_SG1000,
        )
        // Module 3452 Template
        val module_3452 = ModelInfo(
            model = WatchModel.GPR_B1000,
        )
        // Module 5537 Template
        val module_5537 = ModelInfo(
            model = WatchModel.ECB_800,
        )
        // Module 5582 Template
        val module_5582 = ModelInfo(
            model = WatchModel.ECB_900,
        )
        // Module 5536 Template
        val module_5536 = ModelInfo(
            model = WatchModel.GR_B100,
        )
        // Module 5744 Template
        val module_5744 = ModelInfo(
            model = WatchModel.GG_B100X,
        )
        // Module 5594 Template
        val module_5594 = ModelInfo(
            model = WatchModel.GMC_B100,
        )
        // Module 5588 Template
        val module_5588 = ModelInfo(
            model = WatchModel.GWR_B1000,
        )
        // Module 5701 Template
        val module_5701 = ModelInfo(
            model = WatchModel.OCW_S7000,
        )
        // Module 5603 Template
        val module_5603 = ModelInfo(
            model = WatchModel.OCW_S5000,
        )
        // Module 5604 Template
        val module_5604 = ModelInfo(
            model = WatchModel.EQB_1000,
        )
        // Module 5728 Template
        val module_5728 = ModelInfo(
            model = WatchModel.OCW_S400,
        )
        // Module 5597 Template
        val module_5597 = ModelInfo(
            model = WatchModel.OCW_B1300,
        )
        // Module 5618 Template
        val module_5618 = ModelInfo(
            model = WatchModel.ECB_10,
        )
        // Module 5623 Template
        val module_5623 = ModelInfo(
            model = WatchModel.GWF_A1000,
        )
        // Module 5636 Template
        val module_5636 = ModelInfo(
            model = WatchModel.MTG_B2000,
        )
        // Module 5624 Template
        val module_5624 = ModelInfo(
            model = WatchModel.OCW_P2000,
        )
        // Module 5602 Template
        val module_5602 = ModelInfo(
            model = WatchModel.PRT_B70,
        )
        // Module 5657 Template
        val module_5657 = ModelInfo(
            model = WatchModel.GST_B400,
        )
        // Module 5737 Template
        val module_5737 = ModelInfo(
            model = WatchModel.GBA_950,
        )
        // Module 5641 Template
        val module_5641 = ModelInfo(
            model = WatchModel.GBA_900,
        )
        // Module 5748 Template
        val module_5748 = ModelInfo(
            model = WatchModel.GST_B1000,
        )
        // Module 5713 Template
        val module_5713 = ModelInfo(
            model = WatchModel.GWG_B1000,
        )
        // Module 5736 Template
        val module_5736 = ModelInfo(
            model = WatchModel.GA_B010,
        )
        // Module 5756 Template
        val module_5756 = ModelInfo(
            model = WatchModel.GWR_B3000,
        )
        // Module 3599 Template
        val module_3599 = ModelInfo(
            model = WatchModel.GWF_300,
        )
        // Module 5690 Template
        val module_5690 = ModelInfo(
            model = WatchModel.GA_B001,
        )

        // Mapping models to templates based on getWatchSoftModelNumForAirDataServer
        add(module_3461.copy(model = WatchModel.GW_B5600))
        add(module_3461.copy(model = WatchModel.MRG_B5000))

        add(module_3539.copy(model = WatchModel.GMW_B5000_SHARP))
        add(module_3539.copy(model = WatchModel.GW_B5600_SHARP))
        add(module_3539.copy(model = WatchModel.MRG_B5000_SHARP))
        add(module_3539.copy(model = WatchModel.TRN_50))
        add(module_3539.copy(model = WatchModel.GCW_B5000))
        add(module_3539.copy(model = WatchModel.PRJ_BW002))

        add(module_3459.copy(model = WatchModel.GMW_B5000))

        add(module_3509.copy(model = WatchModel.DW_B5600))
        add(module_5690.copy(model = WatchModel.GA_B001))
        add(module_3509.copy(model = WatchModel.G_B001))
        add(module_3509.copy(model = WatchModel.ECB_S10))

        add(module_3575.copy(model = WatchModel.GMW_BZ5000))
        add(
            module_3575.copy(
                model = WatchModel.GW_BX5600,
                protocol = MipProtocol,
                hasNewTimeFormat = true
            )
        )

        add(module_5672.copy(model = WatchModel.MTG_B3000))
        add(module_5672.copy(model = WatchModel.OCW_5700))
        add(module_5672.copy(model = WatchModel.MTG_B3100))
        add(module_5672.copy(model = WatchModel.OCW_5800))

        add(module_5748.copy(model = WatchModel.GST_B1000))
        add(module_5748.copy(model = WatchModel.EQB_1300))

        add(module_5526.copy(model = WatchModel.MTG_B1000))
        add(module_5526.copy(model = WatchModel.MRG_B1000))
        add(module_5526.copy(model = WatchModel.LIW_B1000))
        add(module_5526.copy(model = WatchModel.OCW_S4000))
        add(module_5526.copy(model = WatchModel.OCW_T3000))
        add(module_5526.copy(model = WatchModel.OCW_T4000))

        add(module_3515.copy(model = WatchModel.GBD_H2000))
        add(module_3515.copy(model = WatchModel.DW_GH5600))

        add(module_3516.copy(model = WatchModel.DW_H5600))

        add(module_3552.copy(model = WatchModel.GD_B500))
        add(module_3552.copy(model = WatchModel.GMD_B300))
        add(module_3552.copy(model = WatchModel.WS_B1000))
        add(module_3552.copy(model = WatchModel.F_B100W))

        add(module_3565.copy(model = WatchModel.ABL_100WE))

        add(module_3464.copy(model = WatchModel.GBD_800))
        add(module_3464.copy(model = WatchModel.GMD_B800))

        add(module_5712.copy(model = WatchModel.OCW_T6000))
        add(module_5712.copy(model = WatchModel.OCW_T4000A))
        add(module_5712.copy(model = WatchModel.OCW_T4000B))
        add(module_5712.copy(model = WatchModel.OCW_T4000C))
        add(module_5712.copy(model = WatchModel.MTG_B4000))

        add(module_5719.copy(model = WatchModel.GR_B300))
        add(module_5733.copy(model = WatchModel.GMC_B2100))
        add(module_5718.copy(model = WatchModel.MRG_B2100))
        add(module_5731.copy(model = WatchModel.OCW_SG1000))
        add(module_3452.copy(model = WatchModel.GPR_B1000))

        add(module_5537.copy(model = WatchModel.ECB_800))
        add(module_5582.copy(model = WatchModel.ECB_900))
        add(module_5582.copy(model = WatchModel.ECB_950))
        add(module_5582.copy(model = WatchModel.GST_B200))
        add(module_5582.copy(model = WatchModel.GST_B300))

        add(module_5536.copy(model = WatchModel.GR_B100))
        add(module_5744.copy(model = WatchModel.GG_B100X))
        add(module_5594.copy(model = WatchModel.GMC_B100))
        add(module_5588.copy(model = WatchModel.GWR_B1000))

        add(module_5701.copy(model = WatchModel.OCW_S7000))
        add(module_5603.copy(model = WatchModel.OCW_S5000))
        add(module_5604.copy(model = WatchModel.EQB_1000))
        add(module_5728.copy(model = WatchModel.OCW_S400))
        add(module_5597.copy(model = WatchModel.OCW_B1300))
        add(module_5618.copy(model = WatchModel.ECB_10))
        add(module_5623.copy(model = WatchModel.GWF_A1000))

        add(module_5636.copy(model = WatchModel.MTG_B2000))
        add(module_5636.copy(model = WatchModel.MRG_BF1000))

        add(module_5624.copy(model = WatchModel.OCW_P2000))
        add(module_5602.copy(model = WatchModel.PRT_B70))
        add(module_5657.copy(model = WatchModel.GST_B400))
        add(module_5737.copy(model = WatchModel.GBA_950))
        add(module_5641.copy(model = WatchModel.GBA_900))
        add(module_5713.copy(model = WatchModel.GWG_B1000))
        add(module_5736.copy(model = WatchModel.GA_B010))
        add(module_5756.copy(model = WatchModel.GWR_B3000))
        add(module_3599.copy(model = WatchModel.GWF_300))

        // Add remaining models with default attributes
        WatchModel.entries.forEach { m ->
            if (none { it.model == m }) {
                add(ModelInfo(model = m))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val modelMap: Map<WatchModel, ModelInfo> = modelList.associateBy { it.model }

    /** Pure: derive short name from full device name. */
    private fun deriveShortName(name: String): String =
        if (name.startsWith("CASIO ")) name.substring(6) else name

    private val nameToModelMap = mapOf(
        "GB-5600A" to WatchModel.GSHOCK_TYPE_A_2KEY,
        "GB-6900A" to WatchModel.GSHOCK_TYPE_A_3KEY,
        "GB-5600B" to WatchModel.GSHOCK_TYPE_B_2KEY,
        "GB-6900B" to WatchModel.GSHOCK_TYPE_B_3KEY,
        "GB-X6900B" to WatchModel.GSHOCK_TYPE_B_3KEY,
        "GBA-400" to WatchModel.GMIX_GBA_400,
        "STB-1000" to WatchModel.CASIO_STB_1000,
        "EQB-500" to WatchModel.CASIO_EQB_500,
        "EQB-510" to WatchModel.CASIO_EQB_510,
        "ECB-500" to WatchModel.CASIO_ECB_500,
        "SHB-100" to WatchModel.SHB_100,
        "SHB-200" to WatchModel.SHB_200,
        "EQB-600" to WatchModel.EQB_600,
        "EQB-700" to WatchModel.EQB_700,
        "EQB-501" to WatchModel.EQB_501,
        "EQB-800" to WatchModel.EQB_800,
        "EQB-900" to WatchModel.EQB_900,
        "GST-B100" to WatchModel.GST_B100,
        "GPW-2000" to WatchModel.GPW_2000,
        "GPW-G2000" to WatchModel.GPW_2000,
        "MRG-G2000" to WatchModel.MRG_G2000,
        "OCW-G2000" to WatchModel.OCW_G2000,
        "MODID TEST 5501" to WatchModel.MODID_TEST_5501,
        "MRG-B1000" to WatchModel.MRG_B1000,
        "LIW-B1000" to WatchModel.LIW_B1000,
        "OCW-S4000" to WatchModel.OCW_S4000,
        "MTG-B1000" to WatchModel.MTG_B1000,
        "OCW-T3000" to WatchModel.OCW_T3000,
        "OCW-T4000" to WatchModel.OCW_T4000,
        "OCW-T6000" to WatchModel.OCW_T6000,
        "OCW-T4000A" to WatchModel.OCW_T4000A,
        "OCW-T4000B" to WatchModel.OCW_T4000B,
        "OCW-T4000C" to WatchModel.OCW_T4000C,
        "GR-B300" to WatchModel.GR_B300,
        "MRG-B2100" to WatchModel.MRG_B2100,
        "GMC-B2100" to WatchModel.GMC_B2100,
        "OCW-SG1000" to WatchModel.OCW_SG1000,
        "MTG-B4000" to WatchModel.MTG_B4000,
        "GBA-800" to WatchModel.GBA_800,
        "BSA-B100" to WatchModel.BSA_B100,
        "GBD-800" to WatchModel.GBD_800,
        "GMA-B800" to WatchModel.GMA_B800,
        "GMD-B800" to WatchModel.GMD_B800,
        "GPR-B1000" to WatchModel.GPR_B1000,
        "GMW-B5000" to WatchModel.GMW_B5000,
        "GW-B5600" to WatchModel.GW_B5600,
        "MRG-B5000" to WatchModel.MRG_B5000,
        "GMW-B5000#" to WatchModel.GMW_B5000_SHARP,
        "GW-B5600#" to WatchModel.GW_B5600_SHARP,
        "MRG-B5000#" to WatchModel.MRG_B5000_SHARP,
        "TRN-50" to WatchModel.TRN_50,
        "GCW-B5000" to WatchModel.GCW_B5000,
        "PRJ-BW002" to WatchModel.PRJ_BW002,
        "GR-B100" to WatchModel.GR_B100,
        "ECB-800" to WatchModel.ECB_800,
        "ECB-900" to WatchModel.ECB_900,
        "ECB-950" to WatchModel.ECB_950,
        "GST-B200" to WatchModel.GST_B200,
        "GST-B300" to WatchModel.GST_B300,
        "GMC-B100" to WatchModel.GMC_B100,
        "GG-B100" to WatchModel.GG_B100,
        "GG-B100X" to WatchModel.GG_B100X,
        "PRT-B50" to WatchModel.PRT_B50,
        "GR-B200" to WatchModel.GR_B200,
        "GWR-B1000" to WatchModel.GWR_B1000,
        "OCW-T200" to WatchModel.OCW_T200,
        "OCW-S5000" to WatchModel.OCW_S5000,
        "OCW-B1200" to WatchModel.OCW_B1200,
        "OCW-S6000" to WatchModel.OCW_S6000,
        "OCW-T5000" to WatchModel.OCW_T5000,
        "OCW-S7000" to WatchModel.OCW_S7000,
        "EQB-1000" to WatchModel.EQB_1000,
        "EQB-1100" to WatchModel.EQB_1100,
        "EQB-1200" to WatchModel.EQB_1200,
        "EQB-2000" to WatchModel.EQB_2000,
        "OCW-B1300" to WatchModel.OCW_B1300,
        "OCW-B1400" to WatchModel.OCW_B1400,
        "OCW-S400" to WatchModel.OCW_S400,
        "GBD-H1000" to WatchModel.GBD_H1000,
        "GSR-H1000" to WatchModel.GSR_H1000,
        "GBD-100" to WatchModel.GBD_100,
        "GBX-100" to WatchModel.GBX_100,
        "GBD-200" to WatchModel.GBD_200,
        "GBD-H2000" to WatchModel.GBD_H2000,
        "DW-H5600" to WatchModel.DW_H5600,
        "GM-H5600" to WatchModel.GM_H5600,
        "GPR-H1000" to WatchModel.GPR_H1000,
        "GBD-300" to WatchModel.GBD_300,
        "GBX-H5600" to WatchModel.GBX_H5600,
        "GDG-B100" to WatchModel.GDG_B100,
        "ECB-10" to WatchModel.ECB_10,
        "ECB-20" to WatchModel.ECB_20,
        "ECB-30" to WatchModel.ECB_30,
        "ECB-40" to WatchModel.ECB_40,
        "GWF-A1000" to WatchModel.GWF_A1000,
        "OCW-P2000" to WatchModel.OCW_P2000,
        "MRG-B2000" to WatchModel.MRG_B2000,
        "MTG-B2000" to WatchModel.MTG_B2000,
        "MRG-BF1000" to WatchModel.MRG_BF1000,
        "PRT-B70" to WatchModel.PRT_B70,
        "GST-B400" to WatchModel.GST_B400,
        "GST-B500" to WatchModel.GST_B500,
        "ECB-S100" to WatchModel.ECB_S100,
        "MSG-B100" to WatchModel.MSG_B100,
        "GA-B2100" to WatchModel.GA_B2100,
        "ECB-2000" to WatchModel.ECB_2000,
        "ECB-2300" to WatchModel.ECB_2300,
        "GM-B2100" to WatchModel.GM_B2100,
        "ECB-2200" to WatchModel.ECB_2200,
        "GST-B600" to WatchModel.GST_B600,
        "GBM-2100" to WatchModel.GBM_2100,
        "PRJ-B001" to WatchModel.PRJ_B001,
        "GBA-900" to WatchModel.GBA_900,
        "GBA-950" to WatchModel.GBA_950,
        "MTG-B3000" to WatchModel.MTG_B3000,
        "OCW-5700" to WatchModel.OCW_5700,
        "MTG-B3100" to WatchModel.MTG_B3100,
        "OCW-5800" to WatchModel.OCW_5800,
        "GST-B1000" to WatchModel.GST_B1000,
        "EQB-1300" to WatchModel.EQB_1300,
        "DW-B5600" to WatchModel.DW_B5600,
        "GA-B001" to WatchModel.GA_B001,
        "G-B001" to WatchModel.G_B001,
        "ECB-S10" to WatchModel.ECB_S10,
        "GWG-B1000" to WatchModel.GWG_B1000,
        "PRW-B1000" to WatchModel.PRW_B1000,
        "GD-B500" to WatchModel.GD_B500,
        "ABL-100WE" to WatchModel.ABL_100WE,
        "GMD-B300" to WatchModel.GMD_B300,
        "WS-B1000" to WatchModel.WS_B1000,
        "F-B100W" to WatchModel.F_B100W,
        "GA-B010" to WatchModel.GA_B010,
        "GMW-BZ5000" to WatchModel.GMW_BZ5000,
        "GW-BX5600" to WatchModel.GW_BX5600,
        "GWR-B3000" to WatchModel.GWR_B3000,
        "OCW-P3000" to WatchModel.OCW_P3000,
        "GST-W1000" to WatchModel.GST_W1000,
        "DW-GH5600" to WatchModel.DW_GH5600,
        "GWF-300" to WatchModel.GWF_300,
    )

    /** Pure: map short name prefix to WatchModel. */
    private fun resolveModel(shortName: String): WatchModel =
        nameToModelMap[shortName] ?: WatchModel.GENERIC

    /** Pure: look up ModelInfo, falling back to GENERIC. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun resolveModelInfo(model: WatchModel): ModelInfo =
        modelMap[model] ?: modelMap.getValue(WatchModel.GENERIC)

    /** Pure: build a complete new State from a device name. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildState(name: String): State {
        val shortName = deriveShortName(name)
        val model = resolveModel(shortName)
        val info = resolveModelInfo(model)
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
