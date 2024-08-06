package org.avmedia.gShockPhoneSync.services

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

class DnDModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
            val notificationManager =
                context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.let {
                val currentInterruptionFilter = it.currentInterruptionFilter

                when (currentInterruptionFilter) {
                    NotificationManager.INTERRUPTION_FILTER_ALL -> {
                        ProgressEvents.onNext("DnD Off")
                    }

                    in setOf(
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                        NotificationManager.INTERRUPTION_FILTER_NONE,
                        NotificationManager.INTERRUPTION_FILTER_ALARMS
                    ) -> {
                        ProgressEvents.onNext("DnD On")
                    }

                    else -> {
                        Timber.d("DnDModeReceiver", "Unknown DnD mode")
                    }
                }
            }
        }
    }
}