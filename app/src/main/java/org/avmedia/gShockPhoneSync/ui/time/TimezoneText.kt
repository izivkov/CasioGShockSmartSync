/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import android.view.View
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import timber.log.Timber
import java.util.*

class TimezoneText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    init {
        waitForInitialization()
    }

    private fun waitForInitialization() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.WatchInitializationCompleted -> {
                        text = TimeZone.getDefault().id
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

}
