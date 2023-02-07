/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 10:38 a.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import org.avmedia.gShockPhoneSync.R
import org.jetbrains.anko.contentView

object Utils {

    fun snackBar(view: View, message: String) {

        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setActionTextColor(Color.BLUE)
            .setBackgroundTint(ContextCompat.getColor(view.context, R.color.grey_700))
            .setTextColor(Color.WHITE)
            .show()
    }

    fun snackBar(context: Context, message: String) {
        snackBar((context as Activity).contentView!!, message)
    }
}