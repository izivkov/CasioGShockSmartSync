/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.time

import android.content.Context
import android.util.AttributeSet
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.customComponents.CacheableSubscribableTextView
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

open class HomeTime @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CacheableSubscribableTextView(context, attrs, defStyleAttr) {

    init {
        // Listen on HomeTime update events
        createSubscription()
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

    private fun createSubscription(): Disposable =
        ProgressEvents.connectionEventsFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    // If we have disconnected, close the menu. Otherwise this menu will appear on the connection screen.
                    ProgressEvents.lookupEvent("HomeTimeUpdated") -> {
                        runBlocking {
                            val homeTime = api().getHomeTime()
                            text = homeTime
                        }
                    }
                }
            }
            .subscribe(
                { },
                { throwable -> Timber.i("Got error on subscribe: $throwable") })
}
