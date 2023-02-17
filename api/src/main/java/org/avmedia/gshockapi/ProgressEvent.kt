/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 3:48 p.m.
 */

package org.avmedia.gshockapi

import android.annotation.SuppressLint
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.processors.PublishProcessor
import timber.log.Timber

/**
 * This class is used to send RX events to the rest of the library and to the application to inform
 * them of some internal events. For example, when a connection is established, a `ConnectionSetupComplete`
 * event is broadcast. If the application is listening on this event, it can change its UI to reflect this.
 * The app can call [Subscriber.start] method to start listening to events.
 * Here is an example which can reside in the `MainActivity`:
 *
 * ```
 * private fun createSubscription() {
 *    ProgressEvents.subscriber.start(this.javaClass.simpleName,
 *        {
 *            when (it) {
 *                ProgressEvents.Events.ConnectionSetupComplete -> {
 *                    InactivityWatcher.start(this)
 *                }
 *
 *                ProgressEvents.Events.Disconnect -> {
 *                    Timber.i("onDisconnect")
 *                    InactivityWatcher.cancel()
 *                }
 *
 *                ProgressEvents.Events.ConnectionFailed -> {
 *                    // Do something
 *                }
 *
 *                ProgressEvents.Events.WatchInitializationCompleted -> {
 *                }
 *            }
 *        }, { throwable ->
 *            Timber.d("Got error on subscribe: $throwable")
 *            throwable.printStackTrace()
 *        })
 * }
 * ```
 * @see Events
 *
 */

object ProgressEvents {

    val subscriber = Subscriber()

    private val eventsProcessor: PublishProcessor<Events> = PublishProcessor.create()

    class Subscriber {
        private val subscribers: Set<String> = LinkedHashSet<String>()

        /**
         * Call this from anywhere to start listening to [ProgressEvents].
         *
         * @param name This should be a unique name to prevent multiple subscriptions. Only one
         * subscription per name is allowed. The caller can use its class name (`this.javaClass.simpleName`) to ensure uniqueness:
         */
        @SuppressLint("CheckResult")
        fun start(name: String, onNextStr: Consumer<in Events>, onError: Consumer<in Throwable>) {
            if (subscribers.contains(name)) {
                return // do not allow multiple subscribers with same name
            }

            eventsProcessor.observeOn(AndroidSchedulers.mainThread()).doOnNext(onNextStr)
                .doOnError(onError).subscribe({}, onError)
            (subscribers as LinkedHashSet).add(name)
        }

        /**
         * Stop listening on [ProgressEvents]
         *
         * @param name Name of the subscriber. This is the unique name passes to [start]
         */
        @SuppressLint("CheckResult")
        fun stop(name: String) {
            eventsProcessor.unsubscribeOn(AndroidSchedulers.mainThread())
            (subscribers as LinkedHashSet).remove(name)
        }
    }

    val connectionEventsFlowable = (eventsProcessor as Flowable<Events>)

    /**
     * The application can broadcast its own events by calling this function.
     * Also, the application can extend this class and add its own events.
     *
     *
     * For example, we can broadcast that the calendar has been updated like this:
     * ```
     *  ProgressEvents.onNext(ProgressEvents.Events.CalendarUpdated)
     * ```
     * @param e [Event] to broadcast
     */
    fun onNext(eventName: String) {
        if (eventsProcessor.hasSubscribers()) {
            return eventsProcessor.onNext(builtinEventMap[eventName])
        }
    }

    fun lookupEvent(eventName:String): Events? {
        return builtinEventMap[eventName]
    }

    fun addEvent(eventName: String) {
        if (builtinEventMap.containsKey(eventName)) {
            Timber.d("Event $eventName")
            return
        }

        builtinEventMap[eventName] = Events()
    }

    fun getPayload (eventName: String): Any? {
        return builtinEventMap[eventName]?.payload
    }

    fun addPayload (eventName: String, payload: Any?) {
        builtinEventMap[eventName]?.payload = payload
    }

    open class Events
        (var payload: Any? = null) {
    }

    private var builtinEventMap = mutableMapOf<String, Events>(
        Pair("Init", Events()),
        Pair("ConnectionStarted", Events()),
        Pair("ConnectionSetupComplete", Events()),
        Pair("Disconnect", Events()),
        Pair("AlarmDataLoaded", Events()),
        Pair("NotificationsEnabled", Events()),
        Pair("NotificationsDisabled", Events()),
        Pair("WatchInitializationCompleted", Events()),
        Pair("AllPermissionsAccepted", Events()),
        Pair("ButtonPressedInfoReceived", Events()),
        Pair("ConnectionFailed", Events()),
        Pair("SettingsLoaded", Events()),
        Pair("NeedToUpdateUI", Events()),
        Pair("CalendarUpdated", Events()),
        Pair("HomeTimeUpdated", Events()),
    )
}