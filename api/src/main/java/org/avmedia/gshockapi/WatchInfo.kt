package org.avmedia.gshockapi

/**
 * This class keeps information about the characteristics of the currently connected watch.
 * Based on that, the application can display different information.
 */
data object WatchInfo {
    var name: String = ""
    var shortName = ""
    private var address: String = ""
    var model = WatchModel.UNKNOWN

    // Default parameters for watch characteristics
    var worldCitiesCount = 2
    var dstCount = 3
    var alarmCount = 5
    var hasAutoLight = false
    var hasReminders = false
    var shortLightDuration = "2s"
    var longLightDuration = "4s"
    var weekLanguageSupported = true
    var worldCities = true
    var hasTemperature = true
    var hasBatteryLevel = true
    var batteryLevelLowerLimit = 15
    var batteryLevelUpperLimit = 20
    var alwaysConnected = false
    var findButtonUserDefined = false
    var hasPowerSavingMode = true
    var chimeInSettings = false
    var vibrate = false
    var hasHealthFunctions = false
    var hasMessages = false
    var hasDateFormat = true

    enum class WatchModel {
        GA, GW, DW, GMW, GPR, GST, MSG, GB001, GBD, MRG_B5000, GCW_B5000, EQB, ECB, ABL_100, DW_H5600, UNKNOWN,
    }

    data class ModelInfo(
        var model: WatchModel,
        var worldCitiesCount: Int = 2,
        var dstCount: Int = 1,
        var alarmCount: Int = 5,
        var hasAutoLight: Boolean = false,
        var hasReminders: Boolean = false,
        var shortLightDuration: String = "1.5s",
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
    )

    private val models = listOf(
        ModelInfo(
            model = WatchModel.GW,
            worldCitiesCount = 6,
            dstCount = 3,
            hasAutoLight = true,
            hasReminders = true,
            shortLightDuration = "2s",
            longLightDuration = "4s",
            batteryLevelLowerLimit = 9,
            batteryLevelUpperLimit = 19
        ),
        ModelInfo(
            model = WatchModel.MRG_B5000,
            worldCitiesCount = 6,
            dstCount = 3,
            hasAutoLight = true,
            hasReminders = true,
            shortLightDuration = "2s",
            longLightDuration = "4s",
            batteryLevelLowerLimit = 9,
            batteryLevelUpperLimit = 19
        ),
        ModelInfo(
            model = WatchModel.GCW_B5000,
            worldCitiesCount = 6,
            dstCount = 3,
            hasAutoLight = true,
            hasReminders = true,
            shortLightDuration = "2s",
            longLightDuration = "4s",
            batteryLevelLowerLimit = 9,
            batteryLevelUpperLimit = 19
        ),
        ModelInfo(
            model = WatchModel.GMW,
            worldCitiesCount = 6,
            dstCount = 3,
            hasAutoLight = true,
            hasReminders = true,
            shortLightDuration = "2s",
            longLightDuration = "4s",
            batteryLevelLowerLimit = 9,
            batteryLevelUpperLimit = 19
        ),
        ModelInfo(
            model = WatchModel.GST,
            hasAutoLight = false,
            hasReminders = true
        ),
        ModelInfo(
            model = WatchModel.ABL_100,
            hasAutoLight = false,
            hasReminders = false,
            hasTemperature = false,
            hasBatteryLevel = false
        ),
        ModelInfo(
            model = WatchModel.GA,
            hasAutoLight = false,
            hasReminders = true
        ),
        ModelInfo(
            model = WatchModel.GB001,
            hasAutoLight = true,
            hasReminders = false
        ),
        ModelInfo(
            model = WatchModel.MSG,
            hasAutoLight = false,
            hasReminders = true
        ),
        ModelInfo(
            model = WatchModel.GPR,
            hasAutoLight = true,
            hasReminders = false,
            weekLanguageSupported = false
        ),
        ModelInfo(
            model = WatchModel.DW_H5600,
            alarmCount = 4,
            hasHealthFunctions = false,
            hasMessages = false,
            vibrate = true,
            chimeInSettings = true,
            hasAutoLight = true,
            findButtonUserDefined = true,
            hasReminders = false,
            shortLightDuration = "1.5s",
            longLightDuration = "5s",
            hasBatteryLevel = false,
            alwaysConnected = true,
            hasDateFormat = false,
        ),
        ModelInfo(
            model = WatchModel.DW,
            hasAutoLight = true,
            hasReminders = false
        ),
        ModelInfo(
            model = WatchModel.GBD,
            hasAutoLight = true,
            hasReminders = false,
            worldCities = false,
            hasTemperature = false
        ),
        ModelInfo(
            model = WatchModel.EQB,
            hasAutoLight = true,
            hasReminders = false,
            worldCities = false,
            hasTemperature = false
        ),
        ModelInfo(
            model = WatchModel.ECB,
            hasAutoLight = true,
            hasReminders = false,
            hasTemperature = false,
            hasBatteryLevel = false,
            alwaysConnected = true,
            findButtonUserDefined = true,
            hasPowerSavingMode = false
        ),
        ModelInfo(model = WatchModel.UNKNOWN)
    )

    private val modelMap = models.associateBy { it.model }

    fun setNameAndModel(name: String) {
        this.name = name

        val parts = this.name.split(" ")
        if (parts.size > 1) {
            shortName = parts[1]
        }

        model = when {
            shortName.startsWith("MRG-B5000") -> WatchModel.MRG_B5000
            shortName.startsWith("GCW-B5000") -> WatchModel.GCW_B5000
            shortName.startsWith("ABL-100") -> WatchModel.ABL_100
            shortName.startsWith("G-B001") -> WatchModel.GB001
            shortName.startsWith("GMW") -> WatchModel.GMW
            shortName.startsWith("GST") -> WatchModel.GST
            shortName.startsWith("GPR") -> WatchModel.GPR
            shortName.startsWith("MSG") -> WatchModel.MSG
            shortName.startsWith("GBD") -> WatchModel.GBD
            shortName.startsWith("EQB") -> WatchModel.EQB
            shortName.startsWith("GMB") -> WatchModel.GA
            shortName == "ECB-10" || shortName == "ECB-20" || shortName == "ECB-30" -> WatchModel.ECB
            shortName.startsWith("GA") -> WatchModel.GA
            shortName.startsWith("GB") -> WatchModel.GA
            shortName.startsWith("GW") -> WatchModel.GW
            shortName.startsWith("DW-H5600") -> WatchModel.DW_H5600
            shortName.startsWith("DW") -> WatchModel.DW
            else -> WatchModel.UNKNOWN
        }

        modelMap[model]?.let { modelInfo ->
            with(modelInfo) {
                this@WatchInfo.hasReminders = hasReminders
                this@WatchInfo.hasAutoLight = hasAutoLight
                this@WatchInfo.alarmCount = alarmCount
                this@WatchInfo.worldCitiesCount = worldCitiesCount
                this@WatchInfo.dstCount = dstCount
                this@WatchInfo.shortLightDuration = shortLightDuration
                this@WatchInfo.longLightDuration = longLightDuration
                this@WatchInfo.weekLanguageSupported = weekLanguageSupported
                this@WatchInfo.worldCities = worldCities
                this@WatchInfo.hasTemperature = hasTemperature
                this@WatchInfo.batteryLevelLowerLimit = batteryLevelLowerLimit
                this@WatchInfo.batteryLevelUpperLimit = batteryLevelUpperLimit
                this@WatchInfo.alwaysConnected = alwaysConnected
                this@WatchInfo.findButtonUserDefined = findButtonUserDefined
                this@WatchInfo.hasPowerSavingMode = hasPowerSavingMode
                this@WatchInfo.hasBatteryLevel = hasBatteryLevel
                this@WatchInfo.chimeInSettings = chimeInSettings
                this@WatchInfo.vibrate = vibrate
                this@WatchInfo.hasHealthFunctions = hasHealthFunctions
                this@WatchInfo.hasMessages = hasMessages
                this@WatchInfo.hasDateFormat = hasDateFormat
            }
        } ?: modelMap[WatchModel.UNKNOWN]?.let { defaultModel ->
            with(defaultModel) {
                this@WatchInfo.hasReminders = hasReminders
                this@WatchInfo.hasAutoLight = hasAutoLight
                this@WatchInfo.alarmCount = alarmCount
                this@WatchInfo.worldCitiesCount = worldCitiesCount
                this@WatchInfo.dstCount = dstCount
                this@WatchInfo.shortLightDuration = shortLightDuration
                this@WatchInfo.longLightDuration = longLightDuration
                this@WatchInfo.weekLanguageSupported = weekLanguageSupported
                this@WatchInfo.worldCities = worldCities
                this@WatchInfo.hasTemperature = hasTemperature
                this@WatchInfo.batteryLevelLowerLimit = batteryLevelLowerLimit
                this@WatchInfo.batteryLevelUpperLimit = batteryLevelUpperLimit
                this@WatchInfo.alwaysConnected = alwaysConnected
                this@WatchInfo.findButtonUserDefined = findButtonUserDefined
                this@WatchInfo.hasPowerSavingMode = hasPowerSavingMode
                this@WatchInfo.hasBatteryLevel = hasBatteryLevel
                this@WatchInfo.chimeInSettings = chimeInSettings
                this@WatchInfo.vibrate = vibrate
                this@WatchInfo.hasHealthFunctions = hasHealthFunctions
                this@WatchInfo.hasMessages = hasMessages
                this@WatchInfo.hasDateFormat = hasDateFormat
            }
        }

        ProgressEvents.onNext("DeviceName", this.name)
    }

    fun setAddress(address: String) {
        this.address = address
        ProgressEvents.onNext("DeviceAddress", address)
    }

    fun getAddress(): String = address

    fun reset() {
        address = ""
        name = ""
        shortName = ""
        model = WatchModel.UNKNOWN
    }
}
