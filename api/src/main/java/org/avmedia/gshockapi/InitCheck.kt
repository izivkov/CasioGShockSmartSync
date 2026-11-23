package org.avmedia.gshockapi

import org.avmedia.gshockapi.io.IO

/** What [GShockAPI.init] does with the answer to its button read. */
object InitCheck {
    /** How long init waits for the 0x10 answer (same as 3 s before; stub for the red check). */
    const val BUTTON_TIMEOUT_MS = 3_000L

    /** [complete]: init carries on and reports success. [error]: shown as a blocking Error event. [log]: one line in the App log. */
    data class Decision(val complete: Boolean, val error: String? = null, val log: String? = null)

    fun decide(button: IO.WatchButton?, automatic: Boolean): Decision =
        when (button) {
            null -> Decision(false, "Timed out waiting for button info from the watch")
            IO.WatchButton.INVALID -> Decision(false, "Received invalid button info from the watch")
            else -> Decision(true)
        }
}
