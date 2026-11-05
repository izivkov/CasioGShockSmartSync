package org.avmedia.gshockapi

import android.content.IntentSender

interface ICDPDelegate {
    fun onChooserReady(chooserLauncher: IntentSender)
    fun onError(error: String)
}