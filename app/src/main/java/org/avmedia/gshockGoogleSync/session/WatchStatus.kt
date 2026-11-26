package org.avmedia.gshockGoogleSync.session

/** The status a watch card shows, live from the session. */
enum class WatchStatus {
    /** Connected now. */
    CONNECTED,
    /** Keep connected in background is on, but the watch is not here now: its next reconnect is admitted. */
    WAITING,
    NOT_CONNECTED;

    companion object {
        fun of(address: String, session: SessionState, keepHolder: String?): WatchStatus = when {
            session.phase is SessionPhase.Ready && session.link == LinkState.UP &&
                OpenOnConnect.sameAddress(session.watch.address, address) -> CONNECTED
            OpenOnConnect.sameAddress(keepHolder, address) -> WAITING
            else -> NOT_CONNECTED
        }
    }
}
