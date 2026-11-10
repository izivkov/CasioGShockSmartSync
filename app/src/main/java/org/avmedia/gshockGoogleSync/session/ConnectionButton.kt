package org.avmedia.gshockGoogleSync.session

/**
 * The watch button (or event) that started the current connection. Fixed for the lifetime of a
 * [Session] and known as soon as the session is [SessionPhase.Ready].
 */
enum class ConnectionButton {
    NORMAL,             // LOWER-LEFT long-press
    FROM_ACTION,        // LOWER-RIGHT short-press (action button)
    AUTOMATIC_SYNC,     // watch connected by itself (Auto-Time), no button pressed
    FIND,               // Find Phone pressed on the watch
    ALWAYS_CONNECTED    // always-connected watches (e.g. DW-H5600, ECB)
}