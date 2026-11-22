package org.avmedia.gshockGoogleSync.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** True while any activity of the app is started, i.e. the app is in front of the person. */
object AppForeground {
    private var started = 0
    private val _inFront = MutableStateFlow(false)
    val inFront: StateFlow<Boolean> = _inFront

    fun activityStarted() = synchronized(this) { started++; _inFront.value = true }

    fun activityStopped() = synchronized(this) {
        started = (started - 1).coerceAtLeast(0)
        _inFront.value = started > 0
    }
}
