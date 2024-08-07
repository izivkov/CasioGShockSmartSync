/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-07, 7:53 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-07, 7:52 p.m.
 */

@file:Suppress("EmptyMethod", "EmptyMethod", "ClassName")

package org.avmedia.gShockPhoneSync.ui.settings

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.RadioGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.services.NightWatcher
import org.avmedia.gShockPhoneSync.ui.time.TimerTimeView
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo


// This adapter handles a heterogeneous list of settings.

@Suppress("ClassName")
class SettingsAdapter(private val settings: ArrayList<SettingsModel.Setting>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    @Suppress("ClassName")
    enum class SETTINGS_TYPES {
        LOCALE, OPERATION_SOUND, LIGHT, POWER_SAVING_MODE, TIME_ADJUSTMENT, HAND_ADJUSTMENT, DO_NOT_DISTURB, UNKNOWN
    }

    private lateinit var context: Context

    open inner class ViewHolderBaseSetting(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // add common functionality here
    }

    inner class ViewHolderLocale(itemView: View) : ViewHolderBaseSetting(itemView) {
        val timeFormat: RadioGroup = itemView.findViewById(R.id.time_format_group)
        val dateFormat: RadioGroup = itemView.findViewById(R.id.date_format_group)
        val language: LanguageMenu =
            itemView.findViewById(R.id.language_menu)
    }

    inner class ViewHolderOperationSound(itemView: View) : ViewHolderBaseSetting(itemView) {
        val soundOnOff: SwitchMaterial = itemView.findViewById(R.id.sound_on_off)
    }

    inner class ViewHolderLight(itemView: View) : ViewHolderBaseSetting(itemView) {
        val autoLight: SwitchMaterial =
            itemView.findViewById(R.id.auto_light_on_off)

        val nightOnly: MaterialCheckBox = itemView.findViewById(R.id.night_only)

        val duration: RadioGroup = itemView.findViewById(R.id.light_duration_group)
    }

    inner class ViewHolderPowerSavingMode(itemView: View) : ViewHolderBaseSetting(itemView) {
        val powerSavingMode: SwitchMaterial =
            itemView.findViewById(R.id.power_saving_on_off)
    }

    inner class ViewHolderTimeAdjustment(itemView: View) : ViewHolderBaseSetting(itemView) {
        val timeAdjustment: SwitchMaterial =
            itemView.findViewById(R.id.time_adjustment_on_off)

        val adjustmentTimeMinutes: TextInputEditText =
            itemView.findViewById(R.id.adjustment_time_minutes)

        val timeAdjustmentNotification: MaterialCheckBox =
            itemView.findViewById(R.id.notify_me)
    }

    inner class ViewHolderDnD(itemView: View) : ViewHolderBaseSetting(itemView) {
        val dnd: SwitchMaterial =
            itemView.findViewById(R.id.dnd_on_off)

        val mirrorPhone: MaterialCheckBox =
            itemView.findViewById(R.id.mirror_phone)
    }

    inner class ViewHolderHandAdjustment(itemView: View) : ViewHolderBaseSetting(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        context = parent.context
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

            SETTINGS_TYPES.DO_NOT_DISTURB.ordinal -> {
                val vSetting: View =
                    inflater.inflate(R.layout.setting_item_dnd, parent, false)
                ViewHolderDnD(vSetting)
            }

            SETTINGS_TYPES.HAND_ADJUSTMENT.ordinal -> {
                val vSetting: View =
                    inflater.inflate(R.layout.setting_item_hand_adjustment, parent, false)
                ViewHolderHandAdjustment(vSetting)
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

            SETTINGS_TYPES.DO_NOT_DISTURB.ordinal -> {
                val vhDnD = viewHolder as ViewHolderDnD
                configureDnD(vhDnD, position)
            }

            SETTINGS_TYPES.HAND_ADJUSTMENT.ordinal -> {
                configureHandAdjustment()
            }
        }
    }

    private fun configureHandAdjustment() {
        // EMPTY
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
        if (settings[position] is SettingsModel.DnD) {
            return SETTINGS_TYPES.DO_NOT_DISTURB.ordinal
        }
        if (settings[position] is SettingsModel.HandAdjustment) {
            return SETTINGS_TYPES.HAND_ADJUSTMENT.ordinal
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

        vhLocale.timeFormat.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.twelve_hours) {
                setting.timeFormat = SettingsModel.Locale.TIME_FORMAT.TWELVE_HOURS
            } else {
                setting.timeFormat = SettingsModel.Locale.TIME_FORMAT.TWENTY_FOUR_HOURS
            }
        }

        vhLocale.dateFormat.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.month_day) {
                setting.dateFormat = SettingsModel.Locale.DATE_FORMAT.MONTH_DAY
            } else {
                setting.dateFormat = SettingsModel.Locale.DATE_FORMAT.DAY_MONTH
            }
        }

        val listener = AdapterView.OnItemClickListener { parent, _, position1, _ ->
            when (parent.getItemAtPosition(position1).toString()) {
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

    private fun configureSound(vhOperationSound: ViewHolderOperationSound, position: Int) {
        val setting: SettingsModel.OperationSound =
            settings[position] as SettingsModel.OperationSound
        vhOperationSound.soundOnOff.isChecked = setting.sound == true

        vhOperationSound.soundOnOff.setOnCheckedChangeListener { _, isChecked ->
            setting.sound = isChecked
        }
    }

    private fun configurePowerSavingMode(
        vhPowerSavingMode: ViewHolderPowerSavingMode, position: Int
    ) {
        val setting: SettingsModel.PowerSavingMode =
            settings[position] as SettingsModel.PowerSavingMode
        vhPowerSavingMode.powerSavingMode.isChecked = setting.powerSavingMode == true

        vhPowerSavingMode.powerSavingMode.setOnCheckedChangeListener { _, isChecked ->
            setting.powerSavingMode = isChecked
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureTimeAdjustment(
        vhTimeAdjustment: ViewHolderTimeAdjustment, position: Int
    ) {
        val setting: SettingsModel.TimeAdjustment =
            settings[position] as SettingsModel.TimeAdjustment
        vhTimeAdjustment.timeAdjustment.isChecked = setting.timeAdjustment == true

        vhTimeAdjustment.adjustmentTimeMinutes.setText(setting.adjustmentTimeMinutes.toString())

        vhTimeAdjustment.timeAdjustmentNotification.isChecked =
            setting.timeAdjustmentNotifications == true

        vhTimeAdjustment.timeAdjustment.setOnCheckedChangeListener { _, isChecked ->
            setting.timeAdjustment = isChecked
        }

        vhTimeAdjustment.adjustmentTimeMinutes.setOnClickListener {
            vhTimeAdjustment.adjustmentTimeMinutes.selectAll()
        }

        vhTimeAdjustment.adjustmentTimeMinutes.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                val userInput = vhTimeAdjustment.adjustmentTimeMinutes.text.toString()
                val adjustedMinutes = userInput.toIntOrNull() ?: 0
                setting.adjustmentTimeMinutes = adjustedMinutes

                true // Return true to indicate that the event has been consumed
            } else {
                false // Return false to allow the system to handle the event
            }
        }

        vhTimeAdjustment.adjustmentTimeMinutes.filters = arrayOf(
            InputFilter.LengthFilter(2), TimerTimeView.InputFilterMinMax(0, 59)
        )

        vhTimeAdjustment.timeAdjustmentNotification.setOnCheckedChangeListener { _, isChecked ->
            setting.timeAdjustmentNotifications = isChecked
            LocalDataStorage.setTimeAdjustmentNotification(setting.timeAdjustmentNotifications)
        }
    }

    private fun configureDnD(
        vhDnD: ViewHolderDnD, position: Int
    ) {
        val setting: SettingsModel.DnD =
            settings[position] as SettingsModel.DnD

        vhDnD.dnd.isChecked = setting.dnd == true
        vhDnD.mirrorPhone.isChecked = setting.mirrorPhone
        vhDnD.dnd.isEnabled = !vhDnD.mirrorPhone.isChecked || !WatchInfo.alwaysConnected

        // enable / disable DnD based on Phone Mirroring
        fun isDoNotDisturbOn(context: Context): Boolean {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }
        if (vhDnD.mirrorPhone.isChecked) {
            val isDnDOn = isDoNotDisturbOn(context)
            vhDnD.dnd.isChecked = isDnDOn
        }

        vhDnD.dnd.setOnCheckedChangeListener { _, isChecked ->
            setting.dnd = isChecked
        }

        vhDnD.mirrorPhone.setOnCheckedChangeListener { _, isChecked ->
            setting.mirrorPhone = isChecked
            LocalDataStorage.setMirrorPhoneDnD(setting.mirrorPhone)
            vhDnD.dnd.isEnabled = !isChecked

            if (setting.mirrorPhone) {
                val isDnDOn = isDoNotDisturbOn(context)
                vhDnD.dnd.isChecked = isDnDOn
            }
        }

        val dnDSetActions = arrayOf(
            EventAction("DnD On") {
                if (vhDnD.mirrorPhone.isChecked) {
                    vhDnD.dnd.isChecked = true
                }
            },
            EventAction("DnD Off") {
                if (vhDnD.mirrorPhone.isChecked) {
                    vhDnD.dnd.isChecked = false
                }
            },
        )
        ProgressEvents.runEventActions(this.javaClass.name, dnDSetActions)
    }

    private fun configureLight(vhLight: ViewHolderLight, position: Int) {
        val setting: SettingsModel.Light = settings[position] as SettingsModel.Light
        vhLight.autoLight.isChecked = setting.autoLight == true
        vhLight.nightOnly.isChecked = setting.nightOnly
        vhLight.autoLight.isEnabled = !setting.nightOnly || !WatchInfo.alwaysConnected

        if (setting.duration == SettingsModel.Light.LIGHT_DURATION.TWO_SECONDS) {
            vhLight.duration.check(R.id.light_short)
        } else {
            vhLight.duration.check(R.id.light_long)
        }

        vhLight.autoLight.setOnCheckedChangeListener { _, isChecked ->
            setting.autoLight = isChecked
        }

        vhLight.duration.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.light_short) {
                setting.duration = SettingsModel.Light.LIGHT_DURATION.TWO_SECONDS
            } else {
                setting.duration = SettingsModel.Light.LIGHT_DURATION.FOUR_SECONDS
            }
        }

        vhLight.nightOnly.setOnCheckedChangeListener { _, isChecked ->
            setting.nightOnly = isChecked
            LocalDataStorage.setAutoLightNightOnly(setting.nightOnly)
            vhLight.autoLight.isEnabled = !isChecked

            if (setting.nightOnly) {
                ProgressEvents.onNext(if (NightWatcher.isNight()) "onSunset" else "onSunrise")
            }
        }
    }

    class InputFilterMinMax(private var min: Int, private var max: Int) : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            try {
                val input = Integer.parseInt(dest.toString() + source.toString())
                if (isInRange(min, max, input))
                    return null
            } catch (_: NumberFormatException) {
            }
            return ""
        }

        private fun isInRange(a: Int, b: Int, c: Int): Boolean {
            return if (b > a) c in a..b else c in b..a
        }
    }

    override fun getItemCount(): Int {
        return settings.size
    }
}
