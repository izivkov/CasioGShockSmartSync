/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 5:57 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.avmedia.gShockPhoneSync.MainActivity
import org.avmedia.gShockPhoneSync.R
import kotlin.reflect.KFunction

class AlarmItem @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.card.MaterialCardView(context, attrs, defStyleAttr) {

    private lateinit var onDataChanged: KFunction<Unit>
    private lateinit var alarm: AlarmsModel.Alarm
    private lateinit var alarmTime: TextView

    init {}

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    val picker =
                        MaterialTimePicker.Builder()
                            .setTimeFormat(TimeFormat.CLOCK_12H)
                            .setHour(alarm.hour)
                            .setMinute(alarm.minute)
                            .build()

                    picker.show(
                        (context as MainActivity).supportFragmentManager,
                        picker.toString()
                    )

                    picker.addOnPositiveButtonClickListener {
                        alarm.hour = picker.hour
                        alarm.minute = picker.minute

                        // refresh the alarm list
                        this@AlarmItem.onDataChanged.call()
                    }
                }
            }
            return false
        }
    }

    fun setAlarmData(alarm: AlarmsModel.Alarm) {
        this.alarm = alarm

        alarmTime = findViewById<TextView>(R.id.time)
        alarmTime.setOnTouchListener(OnTouchListener())
    }

    fun setOnDataChange(onDataChanged: KFunction<Unit>) {
        this.onDataChanged = onDataChanged
    }
}
