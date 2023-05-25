/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import kotlinx.coroutines.*
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

open class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    init {
        // Listen on HomeTime update events
        createSubscription()
    }

    // Wait for layout be be loaded, otherwise the layout will overwrite the values when loaded.
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (api().isConnected() && api().isNormalButtonPressed()) {
            runBlocking {
                updateText(api().getHomeTime())
            }
        }
    }

    private fun updateText(value: String) {
        text = value
        invalidate()
        refreshDrawableState()
    }

    private fun createSubscription() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["HomeTimeUpdated"] -> {
                    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    scope.launch {
                        (this@HomeTime).updateText(api().getHomeTime()) // not updating for some reason. Anybody knows why?
                    }
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }
}
