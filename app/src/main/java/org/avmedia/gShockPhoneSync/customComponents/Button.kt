/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 4:05 p.m.
 */

@file:Suppress("EmptyMethod", "EmptyMethod")

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet

@Suppress("EmptyMethod", "EmptyMethod")
open class Button @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.button.MaterialButton(context, attrs, defStyleAttr) {

    open fun show() {
        visibility = VISIBLE
    }

    protected open fun onState() {
    }
}
