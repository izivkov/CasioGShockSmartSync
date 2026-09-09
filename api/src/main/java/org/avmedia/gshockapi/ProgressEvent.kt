/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-28, 3:48 p.m.
 */

package org.avmedia.gshockapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Broadcasts internal library events to subscribers (e.g. the host application).
 *
 * Usage example in MainActivity:
 * ```
 * ProgressEvents.runEventActions(this.javaClass.canonicalName, arrayOf(
 *     EventAction("ConnectionSetupComplete") { println("Connected!") },
 *     EventAction("Disconnect")              { println("Disconnected!") },
 *     EventAction("CustomEvent")             { println("Custom!") },
 * ))
 * ```
 * @see Events
 */

// =============================================================================
// Public API types
// =============================================================================

interface IEventAction {
    val label: String
    val action: () -> Unit
}

data class EventAction(
    override val label: String,
    override val action: () -> Unit
) : IEventAction

object ProgressEvents {

    // =========================================================================
    // Immutable domain type
    // =========================================================================

    /**
     * An event token. Immutable — payload is carried separately in the State,
     * not mutated on the object itself. Identity is by reference (default equals),
     * which is intentional: each registered event is a unique token used as a
     * map key in reverseEventMap.
     */
    class Events

    // =========================================================================
    // Immutable State
    // =========================================================================

    private val builtInEventNames = listOf(
        "Init",
        "ConnectionStarted",
        "ConnectionSetupComplete",
        "Disconnect",
        "AlarmDataLoaded",
        "NotificationsEnabled",
        "NotificationsDisabled",
        "WatchInitializationCompleted",
        "AllPermissionsAccepted",
        "ButtonPressedInfoReceived",
        "ConnectionFailed",
        "SettingsLoaded",
        "NeedToUpdateUI",
        "CalendarUpdated",
        "HomeTimeUpdated",
        "ApiError",
    )

    private data class State(
        // Was Set<String>. Now keyed to each subscriber's collector Job so a
        // re-subscription under the same name (a recreated ViewModel /
        // Composable calling runEventActions again) can cancel-and-replace
        // the old collector instead of being silently ignored, and so
        // stop() has an actual Job to cancel instead of only removing a
        // name from a set that nothing else reads.
        val subscribers: Map<String, Job> = emptyMap(),
        val eventMap: Map<String, Events> = emptyMap(),
        val reverseEventMap: Map<Events, String> = emptyMap(),
        val payloadMap: Map<String, Any?> = emptyMap(),
    )

    private var state: State = buildInitialState()

    // =========================================================================
    // Pure Functional Core
    // =========================================================================

    /** Pure: build the initial state from the built-in event name list. */
    private fun buildInitialState(): State {
        val eventMap = builtInEventNames.associateWith { Events() }
        return State(
            eventMap = eventMap,
            reverseEventMap = eventMap.entries.associate { (k, v) -> v to k },
        )
    }

    /** Pure: derive a new State with one additional event registered. */
    private fun stateWithEvent(current: State, eventName: String): State {
        if (current.eventMap.containsKey(eventName)) return current
        val newEvent = Events()
        return current.copy(
            eventMap = current.eventMap + (eventName to newEvent),
            reverseEventMap = current.reverseEventMap + (newEvent to eventName),
        )
    }

    /** Pure: derive a new State with a subscriber's Job recorded. */
    private fun stateWithSubscriber(current: State, name: String, job: Job): State =
        current.copy(subscribers = current.subscribers + (name to job))

    /** Pure: derive a new State with a subscriber removed. */
    private fun stateWithoutSubscriber(current: State, name: String): State =
        current.copy(subscribers = current.subscribers - name)

    /** Pure: derive a new State with a payload set for a given event name. */
    private fun stateWithPayload(current: State, eventName: String, payload: Any?): State =
        current.copy(payloadMap = current.payloadMap + (eventName to payload))

    // =========================================================================
    // Subscriber
    // =========================================================================

    val subscriber = Subscriber()

    class Subscriber {
        /**
         * Start listening to [ProgressEvents]. If [name] is already subscribed,
         * that existing subscription is cancelled and replaced by this one —
         * safe to call again from a recreated ViewModel/Composable under the
         * same name; it will no longer be silently ignored.
         *
         * @param name    Identifier for this subscription. Re-using an
         *                in-use name replaces the previous subscription.
         * @param eventActions Actions to invoke per event name.
         */
        fun runEventActions(name: String, eventActions: Array<EventAction>) {
            state.subscribers[name]?.cancel()

            val actionMap = eventActions.associateBy { it.label }

            val job = CoroutineScope(Dispatchers.Main).launch {
                eventsFlow.collect { (event, payload) ->
                    try {
                        state.reverseEventMap[event]?.let { eventName ->
                            actionMap[eventName]?.action?.invoke()
                        }
                    } catch (throwable: Throwable) {
                        Timber.d("Error in subscriber '$name': $throwable")
                        throwable.printStackTrace()
                    }
                }
            }

            state = stateWithSubscriber(state, name, job)
        }

        /** Stop listening: cancels the collector and forgets this subscriber. */
        fun stop(name: String) {
            state.subscribers[name]?.cancel()
            state = stateWithoutSubscriber(state, name)
        }
    }

    // =========================================================================
    // Flow — carries (event token, payload) pairs so payload is never mutated
    // =========================================================================

    private val eventsFlow = MutableSharedFlow<Pair<Events, Any?>>(replay = 10)

    // =========================================================================
    // Imperative Shell
    // =========================================================================

    /**
     * Convenience wrapper — delegates to [Subscriber.runEventActions].
     */
    fun runEventActions(name: String, eventActions: Array<EventAction>) {
        subscriber.runEventActions(name, eventActions)
    }

    /**
     * Broadcast an event, optionally with a payload.
     * Custom event names are registered automatically on first use.
     */
    fun onNext(eventName: String, payload: Any? = null) {
        if (!state.eventMap.containsKey(eventName)) {
            state = stateWithEvent(state, eventName)
        }
        state = stateWithPayload(state, eventName, payload)

        val event = state.eventMap[eventName] ?: return

        CoroutineScope(Dispatchers.Main).launch {
            eventsFlow.emit(event to payload)
        }
    }

    /** Retrieve the [Events] token for a given name, or null if not registered. */
    operator fun get(eventName: String): Events? = state.eventMap[eventName]

    /** Retrieve the last payload broadcast for a given event name. */
    fun getPayload(eventName: String): Any? = state.payloadMap[eventName]

    /**
     * Store a payload for an event without broadcasting it.
     * Useful for pre-seeding payload before the event fires.
     */
    fun addPayload(eventName: String, payload: Any?) {
        state = stateWithPayload(state, eventName, payload)
    }
}
