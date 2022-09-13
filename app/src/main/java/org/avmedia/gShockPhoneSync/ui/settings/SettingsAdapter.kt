/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-07, 7:53 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-07, 7:52 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ui.actions.ActionAdapter
import org.avmedia.gShockPhoneSync.ui.actions.ActionsModel
import org.avmedia.gShockPhoneSync.ui.settings.SettingsModel

// This adapter handles a heterogeneous list of settings.

class SettingsAdapter(private val settings: ArrayList<SettingsModel.Setting>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class SETTINGS_TYPES {
        LOCALE, OPERATION_SOUND, LIGHT, POWER_SAVING_MODE, UNKNOWN
    }

    open inner class ViewHolderBaseSetting(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // add common functions here
    }

    inner class ViewHolderLocale(itemView: View) : ViewHolderBaseSetting(itemView)
    inner class ViewHolderOperationSound(itemView: View) : ViewHolderBaseSetting(itemView)
    inner class ViewHolderLight(itemView: View) : ViewHolderBaseSetting(itemView)
    inner class ViewHolderPowerSavingMode(itemView: View) : ViewHolderBaseSetting(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)

        val viewHolder: RecyclerView.ViewHolder = when (viewType) {
            SETTINGS_TYPES.LOCALE.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_locale, parent, false)
                ViewHolderLocale(vSetting)
            }
            SETTINGS_TYPES.OPERATION_SOUND.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_operation_sound, parent, false)
                ViewHolderOperationSound(vSetting)
            }
            SETTINGS_TYPES.LIGHT.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_light, parent, false)
                ViewHolderLight(vSetting)
            }
            SETTINGS_TYPES.POWER_SAVING_MODE.ordinal -> {
                val vSetting: View = inflater.inflate(R.layout.setting_item_power_saving_mode, parent, false)
                ViewHolderPowerSavingMode(vSetting)
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
            SETTINGS_TYPES.OPERATION_SOUND .ordinal -> {
                val vhOperationSound = viewHolder as ViewHolderOperationSound
                configureSound(vhOperationSound, position)
            }
            SETTINGS_TYPES.POWER_SAVING_MODE.ordinal -> {
                val vhPowerSavingMode = viewHolder as ViewHolderPowerSavingMode
                configurePowerSavingMode(vhPowerSavingMode, position)
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

        return SETTINGS_TYPES.UNKNOWN.ordinal
    }

    private fun configureLocale(vhLocale: ViewHolderLocale, position: Int) {
        val setting: SettingsModel.Locale = settings[position] as SettingsModel.Locale
    }

    private fun configureLight(vhLight: ViewHolderLight, position: Int) {
        val setting: SettingsModel.Light = settings[position] as SettingsModel.Light
    }

    private fun configureSound(vhOperationSound: ViewHolderOperationSound, position: Int) {
        val setting: SettingsModel.OperationSound = settings[position] as SettingsModel.OperationSound
    }

    private fun configurePowerSavingMode(vhPowerSavingMode: ViewHolderPowerSavingMode, position: Int) {
        val setting: SettingsModel.PowerSavingMode = settings[position] as SettingsModel.PowerSavingMode
    }

    override fun getItemCount(): Int {
        return settings.size
    }
}
