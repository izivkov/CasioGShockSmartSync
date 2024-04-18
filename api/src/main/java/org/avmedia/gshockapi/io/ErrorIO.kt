package org.avmedia.gshockapi.io

import timber.log.Timber

object ErrorIO {

    suspend fun request(): String {
        return "ERROR"
    }

    fun onReceived(data: String) {
        Timber.d("ErrorIO: onReceived: $data")
    }
}