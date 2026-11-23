package org.avmedia.gshockGoogleSync.homeassistant

/** Turns a failure into a short line for the screens and the app log. Never includes URLs or headers. */
object Diagnostics {
    /** "ClassName: message" */
    fun oneLine(t: Throwable): String =
        t.javaClass.simpleName + (t.message?.let { ": ${it.take(160)}" } ?: "")
}
