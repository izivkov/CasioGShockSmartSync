package com.beamburst.casswatch.ui.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import timber.log.Timber

class PhoneFallbackReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_INDEX = "alarm_index"
        const val EXTRA_EXPECTED_HASH = "expected_hash"
        const val EXTRA_FIRE_INSTANT = "fire_instant"
        const val EXTRA_ALARM_HOUR = "alarm_hour"
        const val EXTRA_ALARM_MINUTE = "alarm_minute"
        const val EXTRA_ALARM_NAME = "alarm_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmIndex = intent.getIntExtra(EXTRA_ALARM_INDEX, -1)
        val expectedHash = intent.getStringExtra(EXTRA_EXPECTED_HASH) ?: return

        val syncRecord = AlarmSyncStorage.loadSyncRecord(context)
        if (syncRecord != null && expectedHash in syncRecord.sentAlarmHashes) {
            Timber.d("Phone fallback: alarm $alarmIndex hash matched sync record; trusting watch")
            return
        }

        Timber.d("Phone fallback: alarm $alarmIndex hash not found; firing AlarmClock intent")
        val hour = intent.getIntExtra(EXTRA_ALARM_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_ALARM_MINUTE, 0)
        val name = intent.getStringExtra(EXTRA_ALARM_NAME)

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, name)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(alarmIntent) }.onFailure {
            Timber.e(it, "Failed to start AlarmClock for fallback")
        }
    }
}
