package org.avmedia.gshockGoogleSync.log

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The app's own logcat, for the App log screen and Share. An app reads its own lines without the READ_LOGS permission
 * (Android 4.1 and later show an app only its own uid's lines). From Android 9 `--uid` keeps the lines of an earlier run
 * of the app (until the phone restarts or the buffer wraps); before that `--pid` gives this run's lines only.
 */
object Logcat {
    /** The `logcat` command: dump and exit, dates with the year, only our lines. */
    fun command(sdk: Int, uid: Int, pid: Int): List<String> =
        listOf("logcat", "-d", "-v", "threadtime", "-v", "year") + listOf("--pid=$pid") // RED CHECK

    /** Our lines now, oldest first; empty when logcat cannot be run. */
    fun read(sdk: Int = android.os.Build.VERSION.SDK_INT, uid: Int = android.os.Process.myUid(), pid: Int = android.os.Process.myPid()): List<String> =
        runCatching {
            val p = ProcessBuilder(command(sdk, uid, pid)).redirectErrorStream(true).start()
            val lines = p.inputStream.bufferedReader().readLines()
            p.waitFor()
            lines
        }.getOrDefault(emptyList())

    // 2026-11-24 12:00:00.123  1234  1250 I Home Assistant: text
    private val LINE = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEFA])\s+(.*?)\s*: (.*)$""")

    /** A logcat line as time, source (the tag, when it is one of ours) and text; null for a line that is not one. */
    fun parse(line: String): LogLine? {
        val m = LINE.matchEntire(line) ?: return null
        return LogLine(m.groupValues[1], LogSource.ofLabel(m.groupValues[3]), m.groupValues[4])
    }

    /** Epoch millis of a parsed line's time (phone zone); 0 when it cannot be read. */
    fun millisOf(time: String): Long = runCatching {
        java.time.LocalDateTime.parse(time.replace(' ', 'T')).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    /** The Share zip: a short header (app version, watch model, Android version) and the lines. Null for no lines. */
    fun zip(target: File, header: Map<String, String>, lines: List<String>): File? {
        if (lines.isEmpty()) return null
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("header.txt"))
            // RED CHECK: no header
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("logcat.txt"))
            zip.write(lines.joinToString("\n", postfix = "\n").toByteArray())
            zip.closeEntry()
        }
        return target
    }
}
