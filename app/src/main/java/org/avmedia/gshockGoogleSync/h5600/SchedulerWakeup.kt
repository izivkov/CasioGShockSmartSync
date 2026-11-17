package org.avmedia.gshockGoogleSync.h5600

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Wakes the scheduler at its next due time with an exact Android alarm. Plain coroutine timers
 * stop while the phone dozes (screen off, lying still), so "1 min after the 08:10 alarm" could run
 * many minutes late; exact alarms are delivered in Doze too. Needs USE_EXACT_ALARM (granted to
 * alarm-type apps); without it an inexact Doze-allowed alarm is used.
 */
object SchedulerWakeup {
    private const val ACTION = "org.avmedia.gshockGoogleSync.h5600.SCHEDULER_WAKE"

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context, 0x5701,
        Intent(context, SchedulerWakeReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Replaces any earlier wake-up. */
    fun schedule(context: Context, at: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(context))
        }.onFailure { Timber.w(it, "scheduler wake-up not set") }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java)?.cancel(intent(context)) }
    }

    /** Keeps the CPU on while a scheduler pass talks to the watch (released after, or after 2 min). */
    suspend fun <T> awake(context: Context, block: suspend () -> T): T {
        val wl = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gshockGoogleSync:H5600Scheduler")
            .apply { setReferenceCounted(false); acquire(2 * 60_000L) }
        return try { block() } finally { if (wl.isHeld) wl.release() }
    }

    /** Short timed wake lock so the CPU stays on until the scheduler loop picks the wake-up up. */
    fun bridge(context: Context) {
        context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gshockGoogleSync:H5600SchedulerWake")
            .apply { setReferenceCounted(false); acquire(30_000L) }
    }
}

@AndroidEntryPoint
class SchedulerWakeReceiver : BroadcastReceiver() {
    @Inject lateinit var runner: H5600ScheduleRunner

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("H5600 scheduler: exact wake-up")
        runner.onWakeupAlarm()
    }
}
