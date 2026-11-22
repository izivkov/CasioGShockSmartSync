package org.avmedia.gshockGoogleSync.webhook

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Webhooks screen is open. It is app-level, not part of the watch session: a watch
 * disconnect (an H5600 drops about every 45 s) must not close the screen or an open editor.
 */
@Singleton
class WebhookScreenState @Inject constructor() {
    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open

    fun show() { _open.value = true }
    fun hide() { _open.value = false }

    /** The Home Assistant screen: its own entry next to Webhooks, same app-level rule. */
    private val _haOpen = MutableStateFlow(false)
    val haOpen: StateFlow<Boolean> = _haOpen

    fun showHa() { _haOpen.value = true }
    fun hideHa() { _haOpen.value = false }
}
