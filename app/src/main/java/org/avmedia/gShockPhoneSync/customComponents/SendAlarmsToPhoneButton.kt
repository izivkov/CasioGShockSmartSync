/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-23, 9:38 a.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ui.alarms.AlarmsModel
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SendAlarmsToPhoneButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
        onState()
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    updatePhoneAlarmClock()
                }
            }
            v?.performClick()
            return false
        }
    }

    private fun updatePhoneAlarmClock() {

        val executorService = Executors.newSingleThreadScheduledExecutor()

        val days = arrayListOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )

        AlarmsModel.alarms.forEachIndexed { index, alarm ->
            if (alarm.enabled) {
                val intent = Intent(
                    AlarmClock.ACTION_SET_ALARM
                )

                intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Casio G-Shock Alarm")
                intent.putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                intent.putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)

                intent.putExtra(
                    AlarmClock.EXTRA_ALARM_SEARCH_MODE,
                    AlarmClock.ALARM_SEARCH_MODE_TIME
                )
                intent.putExtra(AlarmClock.EXTRA_DAYS, days)
                intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)

                /*
                Schedule startActivity() one second apart, to give them time to complete.
                When startActivity() is called, the current activity calls onPause(), which
                normally closes the connection. The 'oneTimeLock' flag will prevent this,
                and will reset itself on each 'disconnect' attempt.
                 */
                executorService.schedule({
                    Connection.oneTimeLock = true
                    context.startActivity(intent)
                }, index.toLong(), TimeUnit.SECONDS)
            }
        }
    }
}
