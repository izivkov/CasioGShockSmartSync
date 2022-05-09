/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import org.json.JSONObject

class ActionList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = ActionAdapter(ActionData.actions)
        layoutManager = LinearLayoutManager(context)
    }
}
