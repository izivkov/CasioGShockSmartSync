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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.customComponents.Button
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
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

                    Utils.snackBar(context, "Setting time...")

                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    scope.launch {
                        api().setTime(TimeZone.getDefault().id)
                        Utils.snackBar(context, "Time Set on Watch")
                        ProgressEvents.onNext("HomeTimeUpdated")
                        Timber.i("<+++++++++++++++++++++++++ Posting HomeTimeUpdated message")
                    }
                }
            }
            v?.performClick()
            return false
        }
    }
}
