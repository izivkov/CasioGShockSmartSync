package org.avmedia.gshockGoogleSync.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if keep-alive was enabled before reboot
            val keepAliveManager = KeepAliveManager.getInstance(context)
            if (keepAliveManager.isEnabled()) {
                keepAliveManager.enable()
            }
        }
    }
}