/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:44 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton

class AlarmChimeSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : com.google.android.material.switchmaterial.SwitchMaterial(context, attrs) {

    init {
        setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (AlarmsModel.alarms.isNotEmpty()) {
                AlarmsModel.alarms[0].hasHourlyChime = isChecked
            }
        })
    }
}
