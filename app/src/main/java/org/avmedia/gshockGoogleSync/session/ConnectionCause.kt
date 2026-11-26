package org.avmedia.gshockGoogleSync.session

/**
 * Who started a connection, fixed when the session is Ready. Only a connection the person caused (a tap on the
 * watch's card, the watch's connect button) opens the watch screens; the watch's own reconnect and a background
 * read never switch screens.
 */
enum class ConnectionCause {
    /** The person: the watch's connect button, a tap on its card, a choice in the watch list. */
    USER,
    /** The watch by itself: connection kind 13 (after sleep) or 14 (after a link loss) as the library reports it. */
    WATCH,
    /** A short background window of the app, for the periodic LifeLog read. */
    BACKGROUND_READ
}
