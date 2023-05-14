/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:38 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Html
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import org.avmedia.gShockPhoneSync.R

open class HandleArrowButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                }
            }
            return false
        }
    }
}
