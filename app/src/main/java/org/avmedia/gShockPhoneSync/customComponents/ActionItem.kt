/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.avmedia.gShockPhoneSync.MainActivity
import org.avmedia.gShockPhoneSync.R
import kotlin.reflect.KFunction

class ActionItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.card.MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var onDataChanged: KFunction<Unit>
    private lateinit var action: IAction

    init {}

    fun setActionData(action: IAction) {
        this.action = action
    }
}
