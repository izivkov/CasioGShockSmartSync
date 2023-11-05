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
import org.avmedia.gshockapi.ProgressEvents.Events
import org.avmedia.gshockapi.ProgressEvents.Subscriber
import timber.log.Timber

/**
 * This class is used to send RX events to the rest of the library and to the application to inform
 * them of some internal events. For example, when a connection is established, a `"ConnectionSetupComplete"`
 * event is broadcast. If the application is listening on this event, it can change its UI to reflect this.
 * The app can call [Subscriber.start] method to start listening to events.
 * Here is an example which can reside in the `MainActivity`:
 *
 * ```
 * private fun listenToProgressEvents() {
 *   ProgressEvents.subscriber.start(this.javaClass.canonicalName,
 *       {
 *           when (it) {
 *              ProgressEvents["ConnectionSetupComplete"] -> {
 *                  println("Got \"ConnectionSetupComplete\" event")
 *              }
 *              ProgressEvents["Disconnect"] -> {
 *                  println("Got \"Disconnect\" event")
 *              }
 *              ProgressEvents["ConnectionFailed"] -> {
 *                  println("Got \"ConnectionFailed\" event")
 *              }
 *              ProgressEvents["WatchInitializationCompleted"] -> {
 *                  println("Got \"WatchInitializationCompleted\" event")
 *              }
 *              ProgressEvents["CustomEvent"] -> {
 *                  println("Got \"CustomEvent\" event")
 *              }
 *           }, { throwable ->
 *              println("Got error on subscribe: $throwable")
 *              throwable.printStackTrace()
 *           })
 *       }
 *   }
 * }
 * ```
 * @see Events
 *
 */

interface IEventAction {
    val label: String
    val action: () -> Unit
}

data class EventAction (
    override val label: String,
    override val action: () -> Unit
) : IEventAction

object ProgressEvents {

    val subscriber = Subscriber()
    private val eventsProcessor: PublishProcessor<Events> = PublishProcessor.create()

    class Subscriber {
        private val subscribers: Set<String> = LinkedHashSet<String>()

        /**
         * Call this from anywhere to start listening to [ProgressEvents].
         *
         * @param name This should be a unique name to prevent multiple subscriptions. Only one
         * subscription per name is allowed. The caller can use its class name (`this.javaClass.canonicalName`) to ensure uniqueness:
         */

        @SuppressLint("CheckResult")
        @Deprecated("This method is deprecated. Use runEventActions() instead.")
        fun start(
            name: String,
            onNextStr: Consumer<in Events>,
            onError: Consumer<in Throwable>,
            filter: (Events) -> Boolean = { true }
        ) {
            if (subscribers.contains(name)) {
                return // do not allow multiple subscribers with same name
            }

            eventsProcessor.observeOn(AndroidSchedulers.mainThread())
                .filter { event -> filter(event) }
                .doOnNext(onNextStr)
                .doOnError(onError)
                .subscribe({}, onError)

            (subscribers as LinkedHashSet).add(name)
        }

        /**
         * Call this from anywhere to start listening to [ProgressEvents].
         *
         * @param name This should be a unique name to prevent multiple subscriptions. Only one
         * subscription per name is allowed. The caller can use its class name (`this.javaClass.canonicalName`) to ensure uniqueness:
         */
        @SuppressLint("CheckResult")
        fun runEventActions(name: String, eventActions: Array<EventAction>) {

            if (subscribers.contains(name)) {
                return // do not allow multiple subscribers with same name
            }

            val runActions: () -> Unit = {
                eventActions.forEach { eventAction ->

                    val filter = { event: Events ->
                        val nameOfEvent = reverseEventMap[event]
                        nameOfEvent != null && nameOfEvent == eventAction.label
                    }

                    val onNext = { _ : Events ->
                        eventAction.action()
                    }

                    val onError = { throwable: Throwable ->
                        Timber.d("Got error on subscribe: $throwable")
                        throwable.printStackTrace()
                    }

                    eventsProcessor.observeOn(AndroidSchedulers.mainThread())
                        .filter { event -> filter(event) }
                        .doOnNext(onNext)
                        .doOnError(onError)
                        .subscribe({}, onError)
                }
            }

            (subscribers as LinkedHashSet).add(name)

            runActions()
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
     * The application can broadcast built in events by calling this function.
     * ```
     *  ProgressEvents.onNext("AllPermissionsAccepted", [payload])
     * ```
     * Also, the app can add its own events by passing an arbitrary string to "onNext":
     *
     * ```
     *  ProgressEvents.onNext("CustomEvent")
     * ```
     * and then listen on this event using the `start()` function, just like it was a built-in event:
     * ```
     * ProgressEvents.subscriber.start(this.javaClass.canonicalName, {
     *  when (it) {
     *      ProgressEvents["CustomEvent"] -> {
     *      // ...
     *    }
    ```
     *
     * @param eventName: Name of event to broadcast
     * @param payload: An optional parameter containing a payload of type `Any?`
     */
    fun onNext(eventName: String, payload: Any? = null) {
        // add it if not in map.
        if (!eventMap.containsKey(eventName)) {
            addEvent(eventName)
        }

        if (eventsProcessor.hasSubscribers()) {
            val event = eventMap[eventName]
            if (event != null) {
                event.payload = payload
                eventsProcessor.onNext(event)
            }
        }
    }

    operator fun get(eventName: String): Events? {
        return eventMap[eventName]
    }

    private fun addEvent(eventName: String) {
        if (eventMap.containsKey(eventName)) {
            Timber.d("Event $eventName")
            return
        }

        val newEvent = Events()
        eventMap[eventName] = newEvent
        reverseEventMap[newEvent] = eventName
    }

    fun getPayload(eventName: String): Any? {
        return eventMap[eventName]?.payload
    }

    fun addPayload(eventName: String, payload: Any?) {
        eventMap[eventName]?.payload = payload
    }

    /**
     * This class contains built-in and custom events which the library can broadcast to the application.
     * Here is a list of the built-in events:
     * ```
     * "Init"
     * "ConnectionStarted"
     * "ConnectionSetupComplete"
     * "Disconnect"
     * "AlarmDataLoaded"
     * "NotificationsEnabled"
     * "NotificationsDisabled"
     * "WatchInitializationCompleted"
     * "AllPermissionsAccepted"
     * "ButtonPressedInfoReceived"
     * "ConnectionFailed"
     * "SettingsLoaded"
     * "NeedToUpdateUI"
     * "CalendarUpdated"
     * "HomeTimeUpdated"
     * "ApiError"
     * ```
     * The App can add their oun arbitrary events like this:
     * ```
     * ProgressEvents.addEvent("MyCustomEvent")
     * ```
     */
    open class Events
        (var payload: Any? = null)

    private var eventMap = mutableMapOf<String, Events>(
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
        Pair("ApiError", Events()),
    )

    private var reverseEventMap = eventMap.entries.associateBy({ it.value }, { it.key }).toMutableMap()
}