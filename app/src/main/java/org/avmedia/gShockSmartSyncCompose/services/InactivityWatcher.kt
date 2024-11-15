/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 9:09 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-30, 9:09 a.m.
 */

package org.avmedia.gShockSmartSyncCompose.services

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.avmedia.gShockSmartSyncCompose.MainActivity.Companion.api
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar
import kotlin.coroutines.CoroutineContext

object InactivityWatcher {
    private const val TIMEOUT: Long = 60 * 3
    private var job: Job? = null
    private val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob()

    fun start(context: Context) {
        cancel()

        job = CoroutineScope(coroutineContext).launch {
            delay(TIMEOUT * 1000)
            AppSnackbar("Disconnecting due to inactivity")
            api().disconnect()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun resetTimer(context: Context) {
        cancel()

        job = CoroutineScope(coroutineContext).launch {
            delay(TIMEOUT * 1000)
            api().disconnect()
            AppSnackbar("Disconnecting due to inactivity")
        }
    }
}
