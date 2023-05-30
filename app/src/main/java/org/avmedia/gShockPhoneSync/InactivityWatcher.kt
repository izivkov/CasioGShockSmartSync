/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gShockPhoneSync

import android.content.Context
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.utils.Utils.snackBar
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object InactivityWatcher {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private const val TIMEOUT: Long = 60 * 3
    private var futureTask: ScheduledFuture<*>? = null

    fun start(context: Context) {
        cancel()

        futureTask = scheduler.schedule({
            api().disconnect(context)
            snackBar(context, "Disconnecting due to inactivity")
        }, TIMEOUT, TimeUnit.SECONDS)
    }

    fun cancel() {
        futureTask?.cancel(true)
    }

    fun resetTimer(context: Context) {
        cancel()

        futureTask = scheduler.schedule({
            api().disconnect(context)
            snackBar(context, "Disconnecting due to inactivity")
        }, TIMEOUT, TimeUnit.SECONDS)
    }
}