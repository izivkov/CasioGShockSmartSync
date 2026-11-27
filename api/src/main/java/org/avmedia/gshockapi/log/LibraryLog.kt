package org.avmedia.gshockapi.log

import timber.log.Timber

/** Where the library's log lines go: one call per line, [source] says which part wrote it. */
fun interface LibraryLogSink {
    fun log(
        source: LibraryLog.Source,
        text: String,
    )
}

/**
 * The library's one log hook. Lines name what happened on the link (no secrets); the app decides where they are kept by
 * setting [sink] at startup. Without a sink they go to Timber.
 */
object LibraryLog {
    /** Which part of the library wrote a line. */
    enum class Source { Watch, Health, Workouts }

    val TIMBER = LibraryLogSink { source, text -> Timber.i("%s: %s", source.name, text) }

    @Volatile var sink: LibraryLogSink = TIMBER

    /** One line; newlines become spaces. A failing sink never breaks the caller. */
    fun log(
        source: Source,
        text: String,
    ) {
        val line = text.replace('\n', ' ').replace('\r', ' ')
        runCatching { sink.log(source, line) }.onFailure { Timber.w("LibraryLog: the sink failed (%s)", it.javaClass.simpleName) }
    }
}
