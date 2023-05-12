/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 9:42 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.alarms

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.ProgressEvents

class AlarmList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = AlarmAdapter(AlarmsModel.alarms)
        layoutManager = LinearLayoutManager(context)

        //if (AlarmsModel.isEmpty()) {
            runBlocking {
                var alarms = api().getAlarms()

                // update the model
                AlarmsModel.alarms.clear()
                AlarmsModel.alarms.addAll(alarms)

                runBlocking {
                    adapter?.notifyDataSetChanged()
                }
            }
        // }
    }
}

