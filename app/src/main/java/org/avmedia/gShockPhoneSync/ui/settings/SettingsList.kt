/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.ui.settings.SettingsFragment.Companion.getFragmentScope
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class SettingsList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    object Cache {
        var adapter: SettingsAdapter? = null
    }

    init {
        adapter =
            Cache.adapter ?: SettingsAdapter(SettingsModel.settings).also { Cache.adapter = it }
        layoutManager = LinearLayoutManager(context)
        listenForUpdateRequest()
    }

    fun init() {
        getFragmentScope().launch(Dispatchers.IO) {
            val settingStr = Gson().toJson(api().getSettings())
            SettingsModel.fromJson(settingStr)

            // Sometimes, especially is production, the UI does not update.
            // Add this here to make sure the UI is updated
            ProgressEvents.onNext("NeedToUpdateUI")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateUI() {
        if (adapter == null) {
            ProgressEvents.onNext("ApiError")
            return
        }
        adapter?.notifyDataSetChanged()
        ProgressEvents.onNext("SettingsLoaded")
    }

    private fun listenForUpdateRequest() {
        val eventActions = arrayOf(
            EventAction("NeedToUpdateUI") {
                updateUI()
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, eventActions)
    }
}
