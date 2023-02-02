/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:38 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.gson.Gson
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.customComponents.Button
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.casio.SettingsSimpleModel

class SendSettingsButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
        onState()
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    updateSettings()
                }
            }
            v?.performClick()
            return false
        }
    }

    private fun updateSettings() {
        var settingsSimpleModel = SettingsSimpleModel()

        val localeSetting = SettingsModel.locale as SettingsModel.Locale
        settingsSimpleModel.language = localeSetting.dayOfWeekLanguage.value
        settingsSimpleModel.timeFormat = localeSetting.timeFormat.value
        settingsSimpleModel.dateFormat = localeSetting.dateFormat.value

        val lightSetting = SettingsModel.light as SettingsModel.Light
        settingsSimpleModel.autoLight = lightSetting.autoLight
        settingsSimpleModel.lightDuration = lightSetting.duration.value

        val powerSavingMode = SettingsModel.powerSavingMode as SettingsModel.PowerSavingMode
        settingsSimpleModel.powerSavingMode = powerSavingMode.powerSavingMode

        val timeAdjustment = SettingsModel.timeAdjustment as SettingsModel.TimeAdjustment
        settingsSimpleModel.timeAdjustment = timeAdjustment.timeAdjustment

        LocalDataStorage.setTimeAdjustmentNotification(timeAdjustment.timeAdjustmentNotifications)

        val buttonTone = SettingsModel.buttonSound as SettingsModel.OperationSound
        settingsSimpleModel.buttonTone = buttonTone.sound

        api().setSettings(settingsSimpleModel)

        Utils.snackBar(context, "Settings Sent to Watch")
    }
}
