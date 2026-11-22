package org.avmedia.gshockGoogleSync.session

/**
 * True while a background connection window is in progress. While it is, connecting and closing do not
 * change the screen, and refused attempts show no error.
 */
object BackgroundConnection {
    @Volatile
    var active: Boolean = false
}
