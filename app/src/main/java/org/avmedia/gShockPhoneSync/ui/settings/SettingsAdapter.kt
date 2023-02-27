/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-07, 7:53 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-07, 7:52 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ui.settings.SettingsModel
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage

// This adapter handles a heterogeneous list of settings.

class SettingsAdapter(private val settings: ArrayList<SettingsModel.Setting>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class SETTINGS_TYPES {
        LOCALE, OPERATION_SOUND, LIGHT, POWER_SAVING_MODE, TIME_ADJUSTMENT, UNKNOWN
    }

    open inner class ViewHolderBaseSetting(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // add common functionality here
    }

    inner class ViewHolderLocale(itemView: View) : ViewHolderBaseSetting(itemView) {
        val timeFormat: RadioGroup = itemView.findViewById<RadioGroup>(R.id.time_format_group)
        val dateFormat: RadioGroup = itemView.findViewById<RadioGroup>(R.id.date_format_group)
        val language: org.avmedia.gShockPhoneSync.ui.settings.LanguageMenu =
            itemView.findViewById(R.id.language_menu)
    }

    inner class ViewHolderOperationSound(itemView: View) : ViewHolderBaseSetting(itemView) {
        val soundOnOff: SwitchMaterial = itemView.findViewById<SwitchMaterial>(R.id.sound_on_off)
    }

    inner class ViewHolderLight(itemView: View) : ViewHolderBaseSetting(itemView) {
        val autoLight: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.auto_light_on_off)
        val duration: RadioGroup = itemView.findViewById<RadioGroup>(R.id.light_duration_group)
    }

    inner class ViewHolderPowerSavingMode(itemView: View) : ViewHolderBaseSetting(itemView) {
        val powerSavingMode: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.power_saving_on_off)
    }

    inner class ViewHolderTimeAdjustment(itemView: View) : ViewHolderBaseSetting(itemView) {
        val timeAdjustment: SwitchMaterial =
            itemView.findViewById<SwitchMaterial>(R.id.time_adjustment_on_off)

        val timeAdjustmentNotification: MaterialCheckBox =
            itemView.findViewById<MaterialCheckBox>(R.id.notify_me)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val viewHolder: RecyclerView.ViewHolder = when (viewType) {
            SETTINGS_TYPES.LOCALE.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_locale, parent, false)
                ViewHolderLocale(vSetting)
            }
            SETTINGS_TYPES.OPERATION_SOUND.ordinal -> {
                val vSetting: View =
                    inflater.inflate(R.layout.setting_item_operation_sound, parent, false)
                ViewHolderOperationSound(vSetting)
            }
            SETTINGS_TYPES.LIGHT.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_light, parent, false)
                ViewHolderLight(vSetting)
            }
            SETTINGS_TYPES.POWER_SAVING_MODE.ordinal -> {
                val vSetting: View =
                    inflater.inflate(R.layout.setting_item_power_saving_mode, parent, false)
                ViewHolderPowerSavingMode(vSetting)
            }
            SETTINGS_TYPES.TIME_ADJUSTMENT.ordinal -> {
                val vSetting: View =
                    inflater.inflate(R.layout.setting_item_time_adjustment, parent, false)
                ViewHolderTimeAdjustment(vSetting)
            }
            else -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_locale, parent, false)
                ViewHolderLocale(vSetting)
            }
        }

        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (viewHolder.itemViewType) {
            SETTINGS_TYPES.LOCALE.ordinal -> {
                val vhLocale = viewHolder as ViewHolderLocale
                configureLocale(vhLocale, position)
            }
            SETTINGS_TYPES.LIGHT.ordinal -> {
                val vhLight = viewHolder as ViewHolderLight
                configureLight(vhLight, position)
            }
            SETTINGS_TYPES.OPERATION_SOUND.ordinal -> {
                val vhOperationSound = viewHolder as ViewHolderOperationSound
                configureSound(vhOperationSound, position)
            }
            SETTINGS_TYPES.POWER_SAVING_MODE.ordinal -> {
                val vhPowerSavingMode = viewHolder as ViewHolderPowerSavingMode
                configurePowerSavingMode(vhPowerSavingMode, position)
            }
            SETTINGS_TYPES.TIME_ADJUSTMENT.ordinal -> {
                val vhTimeAdjustment = viewHolder as ViewHolderTimeAdjustment
                configureTimeAdjustment(vhTimeAdjustment, position)
            }
        }
    }

    // Returns the view type of the item at position for the purposes of view recycling.
    override fun getItemViewType(position: Int): Int {

        if (settings[position] is SettingsModel.Locale) {
            return SETTINGS_TYPES.LOCALE.ordinal
        }
        if (settings[position] is SettingsModel.Light) {
            return SETTINGS_TYPES.LIGHT.ordinal
        }
        if (settings[position] is SettingsModel.OperationSound) {
            return SETTINGS_TYPES.OPERATION_SOUND.ordinal
        }
        if (settings[position] is SettingsModel.PowerSavingMode) {
            return SETTINGS_TYPES.POWER_SAVING_MODE.ordinal
        }
        if (settings[position] is SettingsModel.TimeAdjustment) {
            return SETTINGS_TYPES.TIME_ADJUSTMENT.ordinal
        }

        return SETTINGS_TYPES.UNKNOWN.ordinal
    }

    private fun configureLocale(vhLocale: ViewHolderLocale, position: Int) {
        val setting: SettingsModel.Locale = settings[position] as SettingsModel.Locale
        if (setting.dateFormat == SettingsModel.Locale.DATE_FORMAT.DAY_MONTH) {
            vhLocale.dateFormat.check(R.id.day_month)
        } else {
            vhLocale.dateFormat.check(R.id.month_day)
        }

        if (setting.timeFormat == SettingsModel.Locale.TIME_FORMAT.TWELVE_HOURS) {
            vhLocale.timeFormat.check(R.id.twelve_hours)
        } else {
            vhLocale.timeFormat.check(R.id.twenty_four_hours)
        }

        vhLocale.language.setText(setting.dayOfWeekLanguage.value, false)

        vhLocale.timeFormat.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.twelve_hours) {
                    setting.timeFormat = SettingsModel.Locale.TIME_FORMAT.TWELVE_HOURS
                } else {
                    setting.timeFormat = SettingsModel.Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
                }
            })

        vhLocale.dateFormat.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.month_day) {
                    setting.dateFormat = SettingsModel.Locale.DATE_FORMAT.MONTH_DAY
                } else {
                    setting.dateFormat = SettingsModel.Locale.DATE_FORMAT.DAY_MONTH
                }
            })

        val listener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val selectedItem = parent.getItemAtPosition(position)
                .toString()
            when (selectedItem) {
                "English" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.ENGLISH
                "Spanish" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.SPANISH
                "French" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.FRENCH
                "German" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.GERMAN
                "Italian" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.ITALIAN
                "Russian" -> setting.dayOfWeekLanguage =
                    SettingsModel.Locale.DAY_OF_WEEK_LANGUAGE.RUSSIAN
            }
        }

        vhLocale.language.onItemClickListener = listener
    }

    private fun configureLight(vhLight: ViewHolderLight, position: Int) {
        val setting: SettingsModel.Light = settings[position] as SettingsModel.Light
        vhLight.autoLight.isChecked = setting.autoLight == true

        if (setting.duration == SettingsModel.Light.LIGHT_DURATION.TWO_SECONDS) {
            vhLight.duration.check(R.id.two_seconds)
        } else {
            vhLight.duration.check(R.id.four_seconds)
        }

        vhLight.autoLight.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            setting.autoLight = isChecked
        })

        vhLight.duration.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, checkedId ->
                if (checkedId == R.id.two_seconds) {
                    setting.duration = SettingsModel.Light.LIGHT_DURATION.TWO_SECONDS
                } else {
                    setting.duration = SettingsModel.Light.LIGHT_DURATION.FOUR_SECONDS
                }
            })
    }

    private fun configureSound(vhOperationSound: ViewHolderOperationSound, position: Int) {
        val setting: SettingsModel.OperationSound =
            settings[position] as SettingsModel.OperationSound
        vhOperationSound.soundOnOff.isChecked = setting.sound == true

        vhOperationSound.soundOnOff.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            setting.sound = isChecked
        })
    }

    private fun configurePowerSavingMode(
        vhPowerSavingMode: ViewHolderPowerSavingMode,
        position: Int
    ) {
        val setting: SettingsModel.PowerSavingMode =
            settings[position] as SettingsModel.PowerSavingMode
        vhPowerSavingMode.powerSavingMode.isChecked = setting.powerSavingMode == true

        vhPowerSavingMode.powerSavingMode.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            setting.powerSavingMode = isChecked
        })
    }

    private fun configureTimeAdjustment(
        vhTimeAdjustment: ViewHolderTimeAdjustment,
        position: Int
    ) {
        val setting: SettingsModel.TimeAdjustment =
            settings[position] as SettingsModel.TimeAdjustment
        vhTimeAdjustment.timeAdjustment.isChecked = setting.timeAdjustment == true
        vhTimeAdjustment.timeAdjustmentNotification.isChecked =
            setting.timeAdjustmentNotifications == true

        vhTimeAdjustment.timeAdjustment.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            setting.timeAdjustment = isChecked
        })

        vhTimeAdjustment.timeAdjustmentNotification.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            setting.timeAdjustmentNotifications = isChecked
            LocalDataStorage.setTimeAdjustmentNotification(setting.timeAdjustmentNotifications)
        })
    }

    override fun getItemCount(): Int {
        return settings.size
    }
}
