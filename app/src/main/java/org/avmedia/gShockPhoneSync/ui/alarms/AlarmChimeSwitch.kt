/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:44 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.alarms

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton.OnCheckedChangeListener

class AlarmChimeSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : com.google.android.material.switchmaterial.SwitchMaterial(context, attrs) {

    init {
        setOnCheckedChangeListener(OnCheckedChangeListener { _, isChecked ->
            if (!AlarmsModel.isEmpty()) {
                AlarmsModel.alarms[0].hasHourlyChime = isChecked
            }
        })
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        isChecked = AlarmsModel.alarms[0].hasHourlyChime
    }
}
