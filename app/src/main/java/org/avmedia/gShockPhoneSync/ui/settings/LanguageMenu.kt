/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

class LanguageMenu @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {

    init {
        val items = listOf("English", "Spanish", "French", "German", "Italian", "Russian")
        val adapter = ArrayAdapter(context, R.layout.language_item, R.id.language_text, items)
        setAdapter(adapter)
        createSubscription()
    }

    private fun createSubscription() {

        val eventActions = arrayOf(
            EventAction("Disconnect") {
                dismissDropDown()
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }
}
