/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:44 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.alarms

import android.content.Context
import android.util.AttributeSet
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

class AlarmChimeSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : com.google.android.material.switchmaterial.SwitchMaterial(context, attrs) {

    init {
        setOnCheckedChangeListener { _, isChecked ->
            if (!AlarmsModel.isEmpty()) {
                AlarmsModel.getAlarms()[0].hasHourlyChime = isChecked
            }
        }

        waitForAlarmsLoaded()
    }

    private fun waitForAlarmsLoaded() {
        val eventActions = arrayOf(
            EventAction("Alarms Loaded") {
                isChecked = AlarmsModel.getAlarms().getOrNull(0)?.hasHourlyChime ?: false
            }
        )
        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }
}
