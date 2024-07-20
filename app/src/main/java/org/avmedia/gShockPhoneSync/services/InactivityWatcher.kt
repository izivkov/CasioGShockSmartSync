/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gShockPhoneSync.services

import android.content.Context
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.utils.Utils.snackBar
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

object InactivityWatcher {
    private const val TIMEOUT: Long = 60 * 3
    private var job: Job? = null
    private val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    fun start(context: Context) {
        cancel()

        job = CoroutineScope(coroutineContext).launch {
            delay(TIMEOUT * 1000)
            api().disconnect(context)
            snackBar(context, "Disconnecting due to inactivity")
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun resetTimer(context: Context) {
        cancel()

        job = CoroutineScope(coroutineContext).launch {
            delay(TIMEOUT * 1000)
            api().disconnect(context)
            snackBar(context, "Disconnecting due to inactivity")
        }
    }
}
