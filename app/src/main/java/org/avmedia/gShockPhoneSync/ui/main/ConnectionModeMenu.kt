/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.content.Context
import android.util.AttributeSet
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage

class ConnectionModeMenu @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialAutoCompleteTextView(context, attrs, defStyleAttr) {

    enum class CONNECTION_MODE(val _modeName: String) {
        SINGLE("Single Watch"),
        MULTIPLE("Mult. Watches");

        fun getModeName() = _modeName
    }

    init {
        val items = CONNECTION_MODE.values()
            .map { it.getModeName() } // listOf("Single Watch", "Mult. Watches")
        val adapter =
            ArrayAdapter(context, R.layout.connection_mode_item, R.id.connection_mode_text, items)
        setAdapter(adapter)

        val savedMode = LocalDataStorage.get("ConnectionMode", "Single Watch", context)
        setText(savedMode, false)

        setOnItemClickListener { adapterView, _, i, _ ->
            val selectedItem = adapterView.getItemAtPosition(i).toString()
            val selectedItemStr = selectedItem.toString()
            LocalDataStorage.put("ConnectionMode", selectedItemStr, context)
        }
    }
}
