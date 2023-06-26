/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.actions

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.avmedia.gShockPhoneSync.ui.alarms.AlarmAdapter
import org.avmedia.gShockPhoneSync.ui.settings.SettingsAdapter
import org.avmedia.gShockPhoneSync.ui.settings.SettingsList
import org.avmedia.gShockPhoneSync.ui.settings.SettingsModel
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class ActionList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    object AdapterValue {
        var adapter: ActionAdapter? = null
    }

    init {
        adapter = AdapterValue.adapter ?: ActionAdapter(ActionsModel.actions).also { AdapterValue.adapter = it }
        // adapter = ActionAdapter(ActionsModel.actions)
        layoutManager = LinearLayoutManager(context)
    }

    fun init() {
        ActionsModel.loadData(context)
        watchForDisconnect()
    }

    private fun watchForDisconnect() {
        ProgressEvents.subscriber.start(
            this.javaClass.canonicalName,
            {
                when (it) {
                    ProgressEvents["Disconnect"] -> {
                        shutdown()
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    fun shutdown() {
        ActionsModel.saveData(context)
    }
}
