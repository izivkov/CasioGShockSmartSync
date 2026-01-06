/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 3:48 p.m.
 */

package org.avmedia.gshockapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
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
 *              ProgressEvents[] -> {
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

data class EventAction(
    override val label: String,
    override val action: () -> Unit
) : IEventAction

object ProgressEvents {
    private data class State(
        val subscribers: Set<String> = emptySet(),
        val eventMap: Map<String, Events> = mapOf(
            "Init" to Events(),
            "ConnectionStarted" to Events(),
            "ConnectionSetupComplete" to Events(),
            "Disconnect" to Events(),
            "AlarmDataLoaded" to Events(),
            "NotificationsEnabled" to Events(),
            "NotificationsDisabled" to Events(),
            "WatchInitializationCompleted" to Events(),
            "AllPermissionsAccepted" to Events(),
            "ButtonPressedInfoReceived" to Events(),
            "ConnectionFailed" to Events(),
            "SettingsLoaded" to Events(),
            "NeedToUpdateUI" to Events(),
            "CalendarUpdated" to Events(),
            "HomeTimeUpdated" to Events(),
            "ApiError" to Events()
        ),
        val reverseEventMap: Map<Events, String> = eventMap.entries.associateBy(
            { it.value },
            { it.key })
    )

    private var state = State()
    val subscriber = Subscriber()
    private val eventsFlow = MutableSharedFlow<Events>(replay = 10)

    fun runEventActions(name: String, eventActions: Array<EventAction>) {
        subscriber.runEventActions(name, eventActions)
    }

    class Subscriber {
        /**
         * Call this from anywhere to start listening to [ProgressEvents].
         *
         * @param name This should be a unique name to prevent multiple subscriptions. Only one
         * subscription per name is allowed.
         */
        fun runEventActions(name: String, eventActions: Array<EventAction>) {
            if (state.subscribers.contains(name)) return

            state = state.copy(subscribers = state.subscribers + name)

            val actionMap = eventActions.associateBy { it.label }

            CoroutineScope(Dispatchers.Main).launch {
                eventsFlow
                    .filter { true }
                    .catch { throwable ->
                        Timber.d("Error on subscribe: $throwable")
                        throwable.printStackTrace()
                    }
                    .collect { event ->
                        state.reverseEventMap[event]?.let { eventName ->
                            actionMap[eventName]?.action?.invoke()
                        }
                    }
            }
        }

        /**
         * Stop listening on [ProgressEvents]
         *
         * @param name Name of the subscriber
         */
        fun stop(name: String) {
            state = state.copy(subscribers = state.subscribers - name)
        }
    }

    /**
     * The application can broadcast built-in events by calling this function.
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
        if (!state.eventMap.containsKey(eventName)) {
            addEvent(eventName)
        }

        state.eventMap[eventName]?.apply {
            this.payload = payload
        }?.let { event ->
            if (state.subscribers.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    eventsFlow.emit(event)
                }
            }
        }
    }

    operator fun get(eventName: String): Events? = state.eventMap[eventName]

    private fun addEvent(eventName: String) {
        if (state.eventMap.containsKey(eventName)) return

        val newEvent = Events()
        state = state.copy(
            eventMap = state.eventMap + (eventName to newEvent),
            reverseEventMap = state.reverseEventMap + (newEvent to eventName)
        )
    }

    fun getPayload(eventName: String): Any? = state.eventMap[eventName]?.payload

    fun addPayload(eventName: String, payload: Any?) {
        state.eventMap[eventName]?.payload = payload
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
     * The App can add their own arbitrary events like this:
     * ```
     * ProgressEvents.addEvent("MyCustomEvent")
     * ```
     */
    open class Events(var payload: Any? = null)
}
