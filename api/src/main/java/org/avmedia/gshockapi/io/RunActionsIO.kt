package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

object RunActionsIO {

    suspend fun request(): String {
        return "RUN_ACTIONS"
    }

    fun onReceived(data: String) {
        // 0x0A 02
        Timber.d("RunActionsIO: onReceived: $data")
        if (data == "0x0A 02") {
            ProgressEvents.onNext("RunActions")
        }
    }
}