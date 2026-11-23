package org.avmedia.gshockGoogleSync.ui.settings

import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogLine
import org.avmedia.gshockapi.log.LogSource

/** One shown line; [id] is its place in the log (oldest = 0), a stable key for the list. */
data class ShownLine(val id: Int, val line: LogLine, val rawFrame: Boolean)

/**
 * The App log screen's data: the files are read and parsed once per [load] (off the main thread), newest first; filters
 * and pages work on the parsed lines only. Raw `rx/tx +Ns` frame lines of the watch are most of the volume and are
 * hidden unless asked for.
 */
class AppLogModel(private val read: () -> List<String> = AppLog::readLines) {
    @Volatile private var parsed: List<ShownLine> = emptyList()

    fun load() {
        val raw = read()
        parsed = List(raw.size) { i ->
            val id = raw.size - 1 - i
            val line = AppLog.parse(raw[id])
            ShownLine(id, line, isRawFrame(line))
        }
    }

    /** The first [limit] lines that pass [filter] (null = all sources) and [showRaw]. */
    fun page(filter: LogSource?, showRaw: Boolean, limit: Int): List<ShownLine> {
        // RED CHECK: the old screen: read and parse again per filter, every line, raw frames always
        load()
        if (parsed.isNotEmpty()) return parsed.filter { filter == null || it.line.source == filter }
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
        private val RAW = Regex("""^(rx|tx) \+\d+s\b.*""")
        fun isRawFrame(l: LogLine): Boolean = l.source == LogSource.Watch && RAW.matches(l.text)
    }
}
