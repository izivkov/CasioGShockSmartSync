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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.ui.actions.ActionsModel
import org.avmedia.gShockPhoneSync.ui.setting.SettingsAdapter
import org.avmedia.gshockapi.casio.SettingsSimpleModel
import org.avmedia.gshockapi.utils.ProgressEvents
import org.jetbrains.anko.runOnUiThread
import org.json.JSONObject
import timber.log.Timber

class SettingsList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = SettingsAdapter(SettingsModel.settings)
        layoutManager = LinearLayoutManager(context)

        listenForUpdateRequest()
    }

    fun init() {
        Timber.i("SettingsList: init() called")
        runBlocking {
            val settingSimpleModel = api().getSettings() // update teh model
            val settingStr = Gson().toJson(settingSimpleModel)
            SettingsModel.fromJson(settingStr)
        }
    }

    private fun updateUI() {
        context.runOnUiThread {
            adapter?.notifyDataSetChanged()
            ProgressEvents.onNext(ProgressEvents.Events.SettingsLoaded)
        }
    }

    private fun listenForUpdateRequest(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    // Somebody has made a change to the model...need to update the UI
                    ProgressEvents.Events.NeedToUpdateUI -> {
                        updateUI()
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })
}
