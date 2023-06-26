/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class SettingsList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    object AdapterValue {
        var adapter: SettingsAdapter? = null
    }

    init {
        // Save adapter for re-use
        adapter = AdapterValue.adapter ?: SettingsAdapter(SettingsModel.settings).also { AdapterValue.adapter = it }
        layoutManager = LinearLayoutManager(context)
        listenForUpdateRequest()
    }

    fun init() {
        runBlocking {
            val settingStr = Gson().toJson(api().getSettings())
            SettingsModel.fromJson(settingStr)
        }
    }

    private fun updateUI() {
        adapter?.notifyDataSetChanged()
        ProgressEvents.onNext("SettingsLoaded")
    }

    private fun listenForUpdateRequest() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                // Somebody has made a change to the model...need to update the UI
                ProgressEvents["NeedToUpdateUI"] -> {
                    updateUI()
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }
}
