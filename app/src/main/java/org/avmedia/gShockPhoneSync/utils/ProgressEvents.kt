/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 3:48 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.util.Log
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.processors.PublishProcessor
import timber.log.Timber

object ProgressEvents {

    val subscriber = Subscriber()

    private val eventProcessor: PublishProcessor<Events> = PublishProcessor.create()

    class Subscriber {
        private val subscribers: Set<String> = LinkedHashSet<String>()

        fun start(name: String, onNext: Consumer<in Events>, onError: Consumer<in Throwable>) {
            if (subscribers.contains(name)) {
                return // do not allow multiple subscribers with same name
            }

            eventProcessor.observeOn(AndroidSchedulers.mainThread()).doOnNext(onNext)
                .doOnError(onError).subscribe({}, onError)
            (subscribers as LinkedHashSet).add(name)
        }

        fun stop(name: String) {
            eventProcessor.unsubscribeOn(AndroidSchedulers.mainThread())
            (subscribers as LinkedHashSet).remove(name)
        }
    }

    val connectionEventFlowable = (eventProcessor as Flowable<Events>)

    init {
    }

    fun onNext(e: Events) {
        if (eventProcessor.hasSubscribers()) {
            return eventProcessor.onNext(e)
        }
    }

    open class Events(var payload: Any? = null) {

        object Init : Events()

        object ConnectionStarted : Events()
        object ConnectionSetupComplete : Events()
        object Disconnect : Events()
        object DescriptorRead : Events()
        object DescriptorWrite : Events()
        object CharacteristicChanged : Events()
        object CharacteristicRead : Events()
        object AlarmDataLoaded : Events()
        object NotificationsEnabled : Events()
        object NotificationsDisabled : Events()
        object MtuChanged : Events()
        object WatchDataCollected : Events()
        object WatchInitializationCompleted : Events()
        object AllPermissionsAccepted: Events()
    }
}