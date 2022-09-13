/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

object SettingsModel {
    val settings = ArrayList<SettingsModel.Setting>()

    abstract class Setting (open var title: String)

    class Locale: Setting("Locale") {
        enum class TIME_FORMAT(value: String) {
            TWELVE_HOURS("12h"), TWENTY_FOUR_HOURS("24h"),
        }
        enum class DATE_FORMAT(value: String) {
            MONTH_DAY("Month, day"), DAY_MONTH("Day, month"),
        }

        enum class DAY_OF_WEEK_LANGUAGE(value: String) {
            ENGLISH("English"), SPANISH("Spanish"), FRENCH("French"), GERMAN("German"), ITALIAN("Italian"), RUSSIAN("Russian")
        }

        val timeFormat:TIME_FORMAT = TIME_FORMAT.TWELVE_HOURS
        val dateFormat:DATE_FORMAT = DATE_FORMAT.MONTH_DAY
        val dayOfWeekLanguage: DAY_OF_WEEK_LANGUAGE = DAY_OF_WEEK_LANGUAGE.ENGLISH
    }

    class OperationSound: Setting("Button Sound") {
        enum class SOUND(value:String) {
            BEEP("Beep"), SILENT("Silent")
        }

        val sound:SOUND = SOUND.BEEP
    }

    class Light: Setting("Light") {
        enum class LIGHT_DURATION(value:Int) {
            TWO_SECONDS(2), FOUR_SECONDS(4)
        }

        val autoLight:Boolean = false
        val duration:LIGHT_DURATION = LIGHT_DURATION.TWO_SECONDS
    }

    class PowerSavingMode: Setting("Power Saving Mode") {
        val powerSavingMode:Boolean = false
    }

    init {
        settings.add(Locale())
        settings.add(OperationSound())
        settings.add(Light())
        settings.add(PowerSavingMode())
    }
}