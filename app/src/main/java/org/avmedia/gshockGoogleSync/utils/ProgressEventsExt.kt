package org.avmedia.gshockGoogleSync.utils

import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import java.util.UUID

/**
 * [ProgressEvents.Subscriber.runEventActions] only accepts ONE subscription
 * per name, ever, for the lifetime of the process:
 *
 * ```
 * fun runEventActions(name: String, eventActions: Array<EventAction>) {
 *     if (state.subscribers.contains(name)) return   // <-- silently ignored
 *     ...
 * }
 * ```
 *
 * That's fine for a true process-wide singleton, but every one of our
 * ViewModels and the nav-hosting Composable are tied to a *recreatable*
 * scope (a `@HiltViewModel` can be recreated, a Composable can be recreated
 * on a config change). If any of them are ever recreated, the new
 * instance's call to `runEventActions` under the same hardcoded name is a
 * silent no-op - it never registers, so events it should receive
 * (AlarmsUpdated, NavigateTo, etc.) go nowhere, while a stale closure from
 * the previous instance - bound to a ViewModel/NavController that's no
 * longer the one on screen - stays the only thing subscribed.
 *
 * Giving every subscription a unique name sidesteps this: registration
 * always succeeds. Callers should keep the returned name and pass it to
 * [ProgressEvents.subscriber]`.stop(name)` when their own scope ends
 * (ViewModel.onCleared / Composable DisposableEffect.onDispose), so
 * subscriptions don't pile up indefinitely over the process lifetime.
 */
fun subscribeToProgressEvents(baseName: String, eventActions: Array<EventAction>): String {
    val uniqueName = "$baseName-${UUID.randomUUID()}"
    ProgressEvents.runEventActions(uniqueName, eventActions)
    return uniqueName
}
