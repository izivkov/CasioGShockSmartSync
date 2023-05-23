/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:38 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Html
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import org.avmedia.gShockPhoneSync.R

class InfoButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageButton(context, attrs/*, defStyleAttr*/) {

    private var infoText: String? = ""

    init {
        context.obtainStyledAttributes(
            attrs,
            R.styleable.InfoButton,
            defStyleAttr, 0
        ).apply {
            try {
                infoText = getString(R.styleable.InfoButton_infoText)
            } finally {
                recycle()
            }
        }

        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    val dialogBuilder = AlertDialog.Builder(context)

                    dialogBuilder.setMessage(Html.fromHtml(infoText, FROM_HTML_MODE_LEGACY))
                        .setCancelable(false)
                        .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, _ ->
                            dialog.cancel()
                        })

                    val alert = dialogBuilder.create()
                    alert.setTitle("Info:")
                    alert.show()
                }
            }
            return false
        }
    }

    fun getInfoText(): String? {
        return infoText
    }

    fun setInfoText(showText: String) {
        infoText = showText
        invalidate()
        requestLayout()
    }
}
