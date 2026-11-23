package org.avmedia.gshockapi

import org.avmedia.gshockapi.io.IO

/** What [GShockAPI.init] does with the answer to its button read. */
object InitCheck {
    /**
     * How long init waits for the 0x10 answer: the queue's normal timeout. On a reconnect the connection setup and the
     * queue may hold the exchange for several seconds first, so 3 s was too short and raised a false Error.
     */
    const val BUTTON_TIMEOUT_MS = org.avmedia.gshockapi.casio.CommandTable.REQUEST_TIMEOUT_MS

    /** [complete]: init carries on and reports success. [error]: shown as a blocking Error event. [log]: one line in the App log. */
    data class Decision(val complete: Boolean, val error: String? = null, val log: String? = null)

    /**
     * No answer (or an unreadable one) after the watch connected itself ([automatic]: nobody pressed a button) is not an
     * error: init completes and one line goes to the App log. Only a connection the user started and that really failed
     * is shown as a blocking Error.
     */
    /**
     * [kindKnown] false: no kind answer came on this link at all, so nobody knows a button was pressed (the
     * watch can stay silent on many links in a row). Init ends without an error; the caller releases the link.
     */
    fun decide(button: IO.WatchButton?, automatic: Boolean, kindKnown: Boolean = true): Decision {
        if (button == null && !kindKnown) {
            return Decision(false, log = "The watch answered nothing on this link (no kind answer, no button info); released, no popup")
        }
        val problem =
            when (button) {
                null -> "Timed out waiting for button info from the watch"
                IO.WatchButton.INVALID -> "Received invalid button info from the watch"
                else -> return Decision(true)
            }
        return if (automatic) {
            Decision(true, log = "$problem (automatic reconnect, carrying on)")
        } else {
            Decision(false, error = problem)
        }
    }
}
