package org.avmedia.gshockGoogleSync.homeassistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Home Assistant screen is open. It is app-level, not part of the watch session: a watch
 * disconnect (an H5600 drops about every 45 s) must not close the screen.
 */
@Singleton
class HaScreenState @Inject constructor() {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun show() { _open.value = true }
    fun hide() { _open.value = false }
}
