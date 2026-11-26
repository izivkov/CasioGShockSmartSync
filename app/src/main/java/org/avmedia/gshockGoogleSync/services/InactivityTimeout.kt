package org.avmedia.gshockGoogleSync.services

/**
 * What the app does after the inactivity timeout: back to the main screen. A watch that keeps its link (always
 * connected) stays connected, so the HA push channel, the companion service and the link are untouched; any other watch
 * is disconnected as upstream does.
 */
object InactivityTimeout {
    fun run(keepsLink: Boolean, goHome: () -> Unit, disconnect: () -> Unit, tellDisconnected: () -> Unit) {
        goHome()
        if (keepsLink) return
        disconnect()
        tellDisconnected()
    }
}
