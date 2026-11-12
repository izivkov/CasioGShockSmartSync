package org.avmedia.gshockGoogleSync.session

import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

/**
 * Where the [SessionEventBridge] gets low-level connection events from. A seam so the bridge can
 * be driven by a fake in plain JVM tests.
 */
interface EventSource {
    /** Starts delivering events to [actions]. Closing the returned handle stops delivery. */
    fun subscribe(actions: Array<EventAction>): AutoCloseable

    /** The payload last broadcast with [eventName], read inside an action. */
    fun payload(eventName: String): Any?
}

/** The production [EventSource], backed by the library's [ProgressEvents]. */
object ProgressEventsSource : EventSource {
    override fun subscribe(actions: Array<EventAction>): AutoCloseable =
        ProgressEvents.subscribe(actions)

    override fun payload(eventName: String): Any? = ProgressEvents.getPayload(eventName)
}