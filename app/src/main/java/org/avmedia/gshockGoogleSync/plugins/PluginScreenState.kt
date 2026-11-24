package org.avmedia.gshockGoogleSync.plugins

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which plugin's screen is open (its id), null for none. It is app-level, not part of the watch session: a watch
 * disconnect (an H5600 drops about every 45 s) must not close the screen.
 */
@Singleton
class PluginScreenState @Inject constructor() {
    private val _open = MutableStateFlow<String?>(null)
    val open: StateFlow<String?> = _open

    fun show(pluginId: String) { _open.value = pluginId }
    fun hide() { _open.value = null }
}
