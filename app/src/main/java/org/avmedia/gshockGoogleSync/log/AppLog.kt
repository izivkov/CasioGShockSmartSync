package org.avmedia.gshockGoogleSync.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.avmedia.gshockapi.log.LibraryLog
import org.avmedia.gshockapi.log.LibraryLogSink
import timber.log.Timber

/** Who wrote a line. [label] is what the file and the screen show. */
enum class LogSource(val label: String, private val aliases: Set<String> = emptySet()) {
    // A plugin logs with its id as the Timber tag ("home_assistant"): the same source.
    Watch("Watch"), HomeAssistant("Home Assistant", setOf("home_assistant")), Health("Health"), Workouts("Workouts"), App("App");

    companion object {
        fun ofLabel(label: String): LogSource? = entries.firstOrNull { it.label == label || label in it.aliases }
    }
}

/** One parsed line of the app log. */
data class LogLine(val time: String, val source: LogSource?, val text: String)

/**
 * The app's log lines: everything goes through Timber with the source as tag ("Watch", "Home Assistant", a plugin's
 * id, ...), to logcat (a debug build plants Timber's DebugTree, a release [InfoLogTree]). The App log screen and Share
 * read the app's own logcat ([Logcat]). A line that repeats is written once, then "repeated N times" ([RepeatFilter]).
 * Callers never pass secrets: a line holds names, hosts and short reasons only.
 */
object AppLog {
    @Volatile var clock: () -> Long = System::currentTimeMillis

    private val repeats = RepeatFilter({ clock() })

    /** The last lines in memory, as logcat would show them: the screen's fallback when logcat gives nothing. */
    private val recent = ArrayDeque<String>()
    private const val KEEP_RECENT = 300

    /** Lines before this time are hidden on the screen (Clear); 0 = none. */
    @Volatile var clearedAt: Long = 0

    fun log(source: LogSource, text: String) = write(source.label, text.replace('\n', ' ').replace('\r', ' '), warn = false)

    /** A failure line: [text] and the exception class only (its message can hold a URL or a value). */
    fun failure(source: LogSource, text: String, t: Throwable? = null) =
        write(source.label, if (t == null) text else "$text (${t.javaClass.simpleName})", warn = true)

    private fun write(tag: String, text: String, warn: Boolean) {
        for (line in repeats.filter(tag, text)) {
            if (warn) Timber.tag(tag).w(line) else Timber.tag(tag).i(line)
            remember(tag, if (warn) 'W' else 'I', line)
        }
    }

    private fun remember(tag: String, level: Char, text: String) = synchronized(recent) {
        recent.addLast(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(clock())) + "  0  0 $level $tag: $text")
        while (recent.size > KEEP_RECENT) recent.removeFirst()
    }

    fun recentLines(): List<String> = synchronized(recent) { recent.toList() }

    /** The library's lines go to Timber tagged with their source (Watch, Health, Workouts), like every other line. */
    val librarySink = LibraryLogSink { source, text -> Timber.tag(source.name).i(text) }

    fun install() {
        LibraryLog.sink = librarySink
    }

    /** Hides what was logged so far from the screen (logcat itself is the system's). */
    fun clear() {
        clearedAt = clock()
        synchronized(recent) { recent.clear() }
    }
}

/**
 * A line that repeats is let through once; the repeats are counted and said in one line ("repeated N times") when
 * another line comes or after [windowMs]. Android-free.
 */
class RepeatFilter(private val clock: () -> Long = System::currentTimeMillis, private val windowMs: Long = 10 * 60_000L) {
    private var lastTag: String? = null
    private var last: String? = null
    private var lastAt = 0L
    private var repeats = 0

    /** The lines to write for [text] under [tag] (none while it repeats). */
    @Synchronized
    fun filter(tag: String, text: String): List<String> {
        val now = clock()
        if (false && tag == lastTag && text == last && now - lastAt < windowMs) { // RED CHECK
            repeats++
            return emptyList()
        }
        val out = ArrayList<String>(2)
        if (repeats > 0) out += "(the line before repeated $repeats times)"
        out += text
        lastTag = tag
        last = text
        lastAt = now
        repeats = 0
        return out
    }
}

/** A release build's tree: info and above to logcat, with the line's tag (debug builds use Timber's DebugTree). */
class InfoLogTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= android.util.Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val text = if (t == null) message else "$message (${t.javaClass.simpleName})"
        android.util.Log.println(priority, tag ?: "SmartSync", text)
    }
}
