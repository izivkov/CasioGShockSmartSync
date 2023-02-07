/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gshockapi

import org.json.JSONObject

object SettingsModel {
    val settings = ArrayList<Setting>()

    abstract class Setting(open var title: String)

    private val settingsMap by lazy {
        settings.associateBy { it.title }.toMap()
    }

    val locale by lazy { settingsMap["Locale"] }
    val buttonSound by lazy { settingsMap["Button Sound"] }
    val powerSavingMode by lazy { settingsMap["Power Saving Mode"] }
    val timeAdjustment by lazy { settingsMap["Time Adjustment"] }
    val light by lazy { settingsMap["Light"] }

    class Locale : Setting("Locale") {
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

        var timeFormat: TIME_FORMAT = TIME_FORMAT.TWELVE_HOURS
        var dateFormat: DATE_FORMAT = DATE_FORMAT.MONTH_DAY
        var dayOfWeekLanguage: DAY_OF_WEEK_LANGUAGE = DAY_OF_WEEK_LANGUAGE.ENGLISH
    }

    class OperationSound : Setting("Button Sound") {
        var sound: Boolean = true
    }

    class Light : Setting("Light") {
        enum class LIGHT_DURATION(val value: String) {
            TWO_SECONDS("2s"), FOUR_SECONDS("4s")
        }

        var autoLight: Boolean = false
        var duration: LIGHT_DURATION = LIGHT_DURATION.TWO_SECONDS
    }

    class PowerSavingMode : Setting("Power Saving Mode") {
        var powerSavingMode: Boolean = false
    }

    class TimeAdjustment : Setting("Time Adjustment") {
        var timeAdjustment: Boolean = true
        var timeAdjustmentNotifications: Boolean = false
    }

    init {
        settings.add(Locale())
        settings.add(OperationSound())
        settings.add(Light())
        settings.add(PowerSavingMode())
        settings.add(TimeAdjustment())
    }

    @Synchronized
    fun fromJson(jsonStr: String) {
        /*
        jsonStr:
        {"autoLight":true,"dateFormat":"MM:DD","language":"Spanish","lightDuration":"4s","powerSavingMode":true, "timeAdjustment":true, "timeFormat":"12h","timeTone":false}
         */

        val jsonObj = JSONObject(jsonStr)
        val keys = jsonObj.keys()
        while (keys.hasNext()) {
            val key: String = keys.next()
            val value = jsonObj.get(key)
            when (key) {
                "powerSavingMode" -> {
                    val setting: PowerSavingMode =
                        settingsMap["Power Saving Mode"] as PowerSavingMode
                    setting.powerSavingMode = value == true
                }
                "timeAdjustment" -> {
                    val setting: TimeAdjustment = settingsMap["Time Adjustment"] as TimeAdjustment
                    setting.timeAdjustment = value == true
                }
                "timeTone" -> {
                    val setting: OperationSound = settingsMap["Button Sound"] as OperationSound
                    setting.sound = value == true
                }
                "autoLight" -> {
                    val setting: Light = settingsMap["Light"] as Light
                    setting.autoLight = value == true
                }
                "lightDuration" -> {
                    val setting: Light = settingsMap["Light"] as Light
                    if (value == Light.LIGHT_DURATION.TWO_SECONDS.value) {
                        setting.duration = Light.LIGHT_DURATION.TWO_SECONDS
                    } else {
                        setting.duration = Light.LIGHT_DURATION.FOUR_SECONDS
                    }
                }
                "timeFormat" -> {
                    val setting: Locale = settingsMap["Locale"] as Locale
                    if (value == Locale.TIME_FORMAT.TWELVE_HOURS.value) {
                        setting.timeFormat = Locale.TIME_FORMAT.TWELVE_HOURS
                    } else {
                        setting.timeFormat = Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
                    }
                }
                "dateFormat" -> {
                    val setting: Locale = settingsMap["Locale"] as Locale
                    if (value == Locale.DATE_FORMAT.MONTH_DAY.value) {
                        setting.dateFormat = Locale.DATE_FORMAT.MONTH_DAY
                    } else {
                        setting.dateFormat = Locale.DATE_FORMAT.DAY_MONTH
                    }
                }
                "language" -> {
                    val setting: Locale = settingsMap["Locale"] as Locale
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
                }
            }
        }
    }
}