/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 11:13 a.m.
 */

package org.avmedia.gShockPhoneSync.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

class ConnectionSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ProgressBar(context, attrs, defStyleAttr) {

    init {
        visibility = View.INVISIBLE
        createAppEventsSubscription()
    }

    private fun createAppEventsSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionStarted") {
                visibility = View.VISIBLE
            },
            EventAction("WatchInitializationCompleted") {
                visibility = View.INVISIBLE
            },
            EventAction("ConnectionFailed") {
                visibility = View.INVISIBLE
            },
            EventAction("Disconnect") {
                visibility = View.INVISIBLE
            },
        )

        ProgressEvents.subscriber.runEventActions(this.javaClass.canonicalName, eventActions)
    }
}

