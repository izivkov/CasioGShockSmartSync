/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 11:56 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

class DnDSelector @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : com.google.android.material.switchmaterial.SwitchMaterial(context, attrs) {
    init {
        MainActivity.getLifecycleScope().launch(Dispatchers.IO) {

            val dnDSetActions = arrayOf(
                EventAction("DnD On") {
                    isSelected = true
                },
                EventAction("DnD Off") {
                    isSelected = false
                })

            ProgressEvents.runEventActions(this.javaClass.name, dnDSetActions)
        }
    }
}
