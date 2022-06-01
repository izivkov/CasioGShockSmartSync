/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-06, 7:04 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-06, 7:00 p.m.
 */
package org.avmedia.gShockPhoneSync.customComponents

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.InactivityWatcher
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ble.DeviceCharacteristics
import org.avmedia.gShockPhoneSync.casioB5600.CasioSupport
import org.avmedia.gShockPhoneSync.casioB5600.WatchDataCollector
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import org.avmedia.gShockPhoneSync.utils.Utils
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ActionList @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        adapter = ActionAdapter(ActionsModel.actions)
        layoutManager = LinearLayoutManager(context)
    }

    fun init() {
        ActionsModel.loadData(context)
        watchForDisconnect()
    }

    private fun watchForDisconnect() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,
            {
                when (it) {
                    ProgressEvents.Events.Disconnect -> {
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
