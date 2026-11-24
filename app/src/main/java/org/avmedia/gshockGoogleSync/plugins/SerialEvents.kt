package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The one line the app's events take to the plugins: every source [post]s here and one coroutine runs the work in the
 * order it was posted, so the events of a watch reach the plugins in the order the app saw them (a read of values never
 * passes the disconnect that followed it). A failing piece of work is reported and the line goes on. Android-free.
 */
class SerialEvents(scope: CoroutineScope, private val onError: (Throwable) -> Unit = {}) {
    private val work = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (w in work) {
                try { w() } catch (e: CancellationException) { throw e } catch (e: Throwable) { onError(e) }
            }
        }
    }

    fun post(block: suspend () -> Unit) { work.trySend(block) }
}
