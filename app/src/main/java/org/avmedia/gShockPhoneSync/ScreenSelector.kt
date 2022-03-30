/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:18 p.m.
 */

/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:59 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.annotation.SuppressLint
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import timber.log.Timber
import java.util.*


object ScreenSelector {
    private const val TAG = "ScreenSelector"

    private class NamedScreen(var name: String, var layout: IHideableLayout)
    private var screens: MutableList<NamedScreen> = ArrayList()

    init {
        createAppEventsSubscription()
    }

    fun add(name: String, layout: IHideableLayout) {
        screens.add(NamedScreen(name, layout))
    }

    fun showScreen(name: String) {
        for (screen in screens) {
            if (screen.name == name)
                screen.layout.show()
            else
                screen.layout.hide()
        }
    }

    private fun createAppEventsSubscription(): Disposable =
        ProgressEvents.connectionEventFlowable
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                when (it) {
                    ProgressEvents.Events.PhoneDataCollected -> {
                        showScreen("g-shock screen")
                    }
                    ProgressEvents.Events.Disconnect -> {
                        showScreen("connect screen")
                    }
                }
            }
            .subscribe(
                { },
                { throwable ->
                    Log.d(
                        "EventsSubscription",
                        "Got error on subscribe: $throwable"
                    )
                })

}
