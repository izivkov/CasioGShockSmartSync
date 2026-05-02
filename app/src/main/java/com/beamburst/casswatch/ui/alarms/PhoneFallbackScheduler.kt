package com.beamburst.casswatch.ui.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneFallbackScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun schedule(
        alarmIndex: Int,
        expectedHash: String,
        fireInstant: Long,
        alarmHour: Int,
        alarmMinute: Int,
        alarmName: String?
    ) {
        if (!canScheduleExactAlarms()) {
            Timber.w("SCHEDULE_EXACT_ALARM not granted; skipping fallback for alarm $alarmIndex")
            return
        }
        val wakeAt = fireInstant - 5 * 60 * 1000L
        if (wakeAt <= System.currentTimeMillis()) return

        val intent = Intent(context, PhoneFallbackReceiver::class.java).apply {
            putExtra(PhoneFallbackReceiver.EXTRA_ALARM_INDEX, alarmIndex)
            putExtra(PhoneFallbackReceiver.EXTRA_EXPECTED_HASH, expectedHash)
            putExtra(PhoneFallbackReceiver.EXTRA_FIRE_INSTANT, fireInstant)
            putExtra(PhoneFallbackReceiver.EXTRA_ALARM_HOUR, alarmHour)
            putExtra(PhoneFallbackReceiver.EXTRA_ALARM_MINUTE, alarmMinute)
            putExtra(PhoneFallbackReceiver.EXTRA_ALARM_NAME, alarmName ?: "")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, pendingIntent)
        Timber.d("Scheduled phone fallback for alarm $alarmIndex at epoch $wakeAt")
    }

    fun cancel(alarmIndex: Int) {
        val intent = Intent(context, PhoneFallbackReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmIndex,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    fun cancelAll(indices: List<Int>) = indices.forEach { cancel(it) }
}
