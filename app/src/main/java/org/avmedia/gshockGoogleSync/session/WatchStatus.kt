package org.avmedia.gshockGoogleSync.session

/** RED CHECK stub: the status a watch card shows; the real one in the next commit. */
enum class WatchStatus {
    CONNECTED, WAITING, NOT_CONNECTED;

    companion object {
        fun of(address: String, session: SessionState, keepHolder: String?): WatchStatus = NOT_CONNECTED
    }
}
