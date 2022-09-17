/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.app.NotificationManager
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import org.avmedia.gShockPhoneSync.casio.SettingsTransferObject
import org.avmedia.gShockPhoneSync.casio.WatchDataCollector
import java.text.SimpleDateFormat
import java.util.*

object AutoConfigurator {

    fun configure(context: Context): SettingsTransferObject {
        val settings = SettingsTransferObject()
        val currentLocale: Locale = Locale.getDefault()
        val language = currentLocale.language

        val localeSetting = SettingsModel.locale as SettingsModel.Locale
        when (language) {
            "en" -> settings.language = "English"
            "es" -> settings.language = "Spanish"
            "fr" -> settings.language = "French"
            "de" -> settings.language = "German"
            "it" -> settings.language = "Italian"
            "ru" -> settings.language = "Russian"
            else -> settings.language = "English"
        }

        var dateTimePettern = SimpleDateFormat().toPattern()

        // fr: dd/MM/y HH:mm
        // es: d/M/yy H:mm
        // uk: dd/MM/y HH:mm
        // us: M/d/yy h:mm a
        // de: dd.MM.yy HH:mm
        // it: dd/MM/yy HH:mm
        // ru: dd.MM.y HH:mm
        //
        // https://help.gooddata.com/cloudconnect/manual/date-and-time-format.html

        val datePattern = dateTimePettern.split(" ")[0]
        val timePattern = dateTimePettern.split(" ")[1]

        if (datePattern.lowercase().startsWith("d")) {
            settings.dateFormat = "DD:MM"
        } else {
            settings.dateFormat = "MM:DD"
        }

        if (timePattern[0] == 'h') {
            settings.timeFormat = "12h"
        } else {
            settings.timeFormat = "24h"
        }

        // Button sounds
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        settings.buttonTone =
            notificationManager.currentInterruptionFilter == INTERRUPTION_FILTER_ALL

        // Light
        settings.lightDuration = "2s"
        settings.autoLight = false
        // for autoLight, we may want to use day/time to set off/on

        // Power Save mode
        val batteryLevel:Int = WatchDataCollector.batteryLevelValue.toInt()
        settings.powerSavingMode = batteryLevel <= 15

        return settings
    }
}