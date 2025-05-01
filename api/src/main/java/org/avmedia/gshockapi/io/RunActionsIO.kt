package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

object RunActionsIO {

    suspend fun request(): String {
        return "RUN_ACTIONS"
    }

    fun onReceived(data: String) {
        // 0x0A 02
        if (data == "0x0A 02" && WatchInfo.alwaysConnected) {
            ProgressEvents.onNext("RunActions")
        }
    }
}