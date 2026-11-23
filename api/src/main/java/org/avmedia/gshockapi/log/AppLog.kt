package org.avmedia.gshockapi.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/** Who wrote a line. [label] is what the file and the screen show. */
enum class LogSource(val label: String) {
    Watch("Watch"), HomeAssistant("Home Assistant"), Health("Health"), App("App");

    companion object {
        fun ofLabel(label: String): LogSource? = entries.firstOrNull { it.label == label }
    }
}

/** One parsed line of the app log. */
data class LogLine(val time: String, val source: LogSource?, val text: String)

/**
 * The one app log: the connection events of the watch, Home Assistant calls, health and settings failures.
 * Lines look like `2026-11-23 10:00:00.000 [Home Assistant] text`. The file is rolling: when [file] passes [maxFileBytes] it
 * becomes `<name>.1` (the older `.1` is dropped), so at most two files of that size exist. Callers never pass secrets:
 * a line holds names, hosts and short reasons only.
 */
object AppLog {
    /** Where lines are kept (the main file), null keeps them in memory only. */
    @Volatile var logFile: File? = null

    @Volatile var clock: () -> Long = System::currentTimeMillis

    @Volatile var maxFileBytes: Long = 1024L * 1024L

    private val events = ArrayDeque<String>()
    private const val KEEP_EVENTS = 300

    fun rolledFile(f: File): File = File(f.parentFile, f.name + ".1")

    fun recentEvents(): List<String> = synchronized(events) { events.toList() }

    fun log(source: LogSource, text: String) {
        val line = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(clock())) +
            " [" + source.label + "] " + text.replace('\n', ' ').replace('\r', ' ')
        Timber.i("AppLog: $line")
        synchronized(events) {
            events.addLast(line)
            while (events.size > KEEP_EVENTS) events.removeFirst()
            runCatching {
                logFile?.let { f ->
                    f.parentFile?.mkdirs()
                    if (f.length() + line.length + 1 > maxFileBytes) rotate(f)
                    f.appendText(line + "\n")
                }
            }
        }
    }

    /** A failure line: [text] and the exception class only (its message can hold a URL or a value). */
    fun failure(source: LogSource, text: String, t: Throwable? = null) =
        log(source, if (t == null) text else "$text (${t.javaClass.simpleName})")

    private fun rotate(f: File) {
        val old = rolledFile(f)
        old.delete()
        if (!f.renameTo(old)) f.delete()
    }

    /**
     * Every line on disk, oldest first (older file, then the current one). When the disk gives nothing (no file, a write
     * or read that failed) the in-memory lines are shown: the screen is never empty while lines were logged.
     */
    fun readLines(): List<String> = synchronized(events) {
        val f = logFile ?: return events.toList()
        val disk = runCatching {
            listOf(rolledFile(f), f).filter { it.isFile }.flatMap { it.readLines() }
        }.getOrDefault(emptyList())
        disk.ifEmpty { events.toList() }
    }

    fun parse(line: String): LogLine {
        val m = LINE.matchEntire(line) ?: return LogLine("", null, line)
        return LogLine(m.groupValues[1], LogSource.ofLabel(m.groupValues[2]), m.groupValues[3])
    }

    private val LINE = Regex("""(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[([^\]]+)] (.*)""")

    fun clear() = synchronized(events) {
        events.clear()
        logFile?.let { runCatching { it.delete(); rolledFile(it).delete() } }
        Unit
    }
}
