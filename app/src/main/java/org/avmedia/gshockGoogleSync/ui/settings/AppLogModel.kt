package org.avmedia.gshockGoogleSync.ui.settings

import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogLine
import org.avmedia.gshockGoogleSync.log.LogSource

/** One shown line; [id] is its place in the log (oldest = 0), a stable key for the list. */
data class ShownLine(val id: Int, val line: LogLine, val rawFrame: Boolean)

/**
 * The App log screen's data: the app's logcat is read and parsed once per [load] (off the main thread), newest first; filters
 * and pages work on the parsed lines only. Raw `rx/tx +Ns` frame lines of the watch are most of the volume and are
 * hidden unless asked for.
 */
class AppLogModel(private val read: () -> List<String> = ::ownLines) {
    @Volatile private var parsed: List<ShownLine> = emptyList()

    /** Our own logcat lines (or, when logcat gives nothing, the last lines in memory): only our sources, after Clear. */
    fun load() {
        val raw = read()
        val out = ArrayList<ShownLine>(raw.size)
        for (id in raw.indices.reversed()) {
            val line = org.avmedia.gshockGoogleSync.log.Logcat.parse(raw[id]) ?: continue
            if (line.source == null) continue
            if (AppLog.clearedAt > 0 && org.avmedia.gshockGoogleSync.log.Logcat.millisOf(line.time) < AppLog.clearedAt) continue
            out += ShownLine(id, line, isRawFrame(line))
        }
        parsed = out
    }

    /** The first [limit] lines that pass [filter] (null = all sources) and [showRaw]. */
    fun page(filter: LogSource?, showRaw: Boolean, limit: Int): List<ShownLine> {
        val out = ArrayList<ShownLine>(minOf(limit, parsed.size))
        for (l in parsed) {
            if (out.size >= limit) break
            if (filter != null && l.line.source != filter) continue
            if (!showRaw && l.rawFrame) continue
            out += l
        }
        return out
    }

    companion object {
        const val PAGE = 500

        fun ownLines(): List<String> = org.avmedia.gshockGoogleSync.log.Logcat.read().ifEmpty { AppLog.recentLines() }
        private val RAW = Regex("""^(rx|tx) \+\d+s\b.*""")
        fun isRawFrame(l: LogLine): Boolean = l.source == LogSource.Watch && RAW.matches(l.text)
    }
}
