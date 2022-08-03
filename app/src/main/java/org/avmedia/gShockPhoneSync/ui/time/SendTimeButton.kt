/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-26, 11:02 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.avmedia.gShockPhoneSync.casio.CasioTimeZone
import org.avmedia.gShockPhoneSync.customComponents.Button
import org.avmedia.gShockPhoneSync.utils.Utils
import java.time.Clock
import java.util.*

class SendTimeButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Button(context, attrs, defStyleAttr) {

    init {
        setOnTouchListener(OnTouchListener())
    }

    inner class OnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> {
                    CasioTimeZone.setHomeTime(TimeZone.getDefault().id)

                    sendTimeToWatch()

                    // update the screen with new Home Time
                    CasioTimeZone.rereadHomeTimeFromWatch()

                    Utils.snackBar(context, "Time Sent to Watch")
                }
            }
            v?.performClick()
            return false
        }

        private fun sendTimeToWatch() {
            sendMessage("{action: \"SET_TIME\", value: ${Clock.systemDefaultZone().millis()}}")
        }
    }
}
