/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.ContentInfo
import android.view.translation.ViewTranslationResponse
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

open class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    init {
        // not working, so I commented it out for now.
        // The value is updated from the Main Activity

        // Listen on HomeTime update events
        // createSubscription()
    }

    // Wait for layout be be loaded, otherwise the layout will overwrite the values when loaded.
    override fun onFinishInflate() {
        super.onFinishInflate()
        if (api().isConnected() && api().isNormalButtonPressed()) {
            runBlocking {
                text = api().getHomeTime()
            }
        }
    }

    private fun createSubscription() {
        ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
            when (it) {
                ProgressEvents["HomeTimeUpdated"] -> {
                    runBlocking {
                        // This does not work for me...
                        // Not updating for some reason. Anybody knows why?

                        val homeCity = api().getHomeTime()
                        text = homeCity
                    }
                }
            }
        }, { throwable ->
            Timber.d("Got error on subscribe: $throwable")
            throwable.printStackTrace()
        })
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
    }

    suspend fun update() {
        text = api().getHomeTime()
    }
}
