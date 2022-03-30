/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gShockPhoneSync

import android.app.Notification
import android.content.Context
import android.os.Handler
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.utils.Utils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object InactivityWatcher {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private const val TIMEOUT:Long = 60*3
    private lateinit var futureTask: ScheduledFuture<*>

    fun start (context: Context) {
        futureTask = scheduler.schedule({
            Connection.disconnect(context)
        }, TIMEOUT, TimeUnit.SECONDS)
    }

    fun cancel () {
        if (futureTask != null) {
            futureTask.cancel(true)
        }
    }

    fun resetTimer (context: Context) {
        cancel ()

        futureTask = scheduler.schedule({
            Connection.disconnect(context)
            Utils.toast(context, "Disconnecting due to inactivity")
        }, TIMEOUT, TimeUnit.SECONDS)
    }
}