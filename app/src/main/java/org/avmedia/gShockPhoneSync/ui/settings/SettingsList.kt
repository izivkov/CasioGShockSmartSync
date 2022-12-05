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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ui.setting.SettingsAdapter
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import org.jetbrains.anko.runOnUiThread
import timber.log.Timber

class SettingsList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = SettingsAdapter(SettingsModel.settings)
        layoutManager = LinearLayoutManager(context)

        subscribe("SETTINGS", ::onDataReceived)
        Connection.sendMessage("{ action: 'GET_SETTINGS'}")

        subscribe("TIME_ADJUSTMENT", ::onReceivedTimeAdjustment)
        Connection.sendMessage("{ action: 'GET_TIME_ADJUSTMENT'}")

        listenForUpdateRequest()
    }

    fun init() {
        Timber.i("SettingsList: init() called")
    }

    private fun onDataReceived(data: String) {
        SettingsModel.fromJson(data)
        updateUI()
    }

    private fun onReceivedTimeAdjustment(data: String) {
        SettingsModel.fromJson(data)
        updateUI()
    }

    private fun updateUI () {
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
                        updateUI ()
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })

    @SuppressLint("CheckResult")
    private fun subscribe(subject: String, onDataReceived: (String) -> Unit) {
        WatchDataEvents.addSubject(subject)
        WatchDataEvents.subscribe(this.javaClass.simpleName, subject, onNext = {
            onDataReceived(it as String)
        })
    }
}
