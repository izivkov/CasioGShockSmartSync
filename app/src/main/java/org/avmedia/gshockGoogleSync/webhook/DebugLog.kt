package org.avmedia.gshockGoogleSync.webhook

import android.util.Log
import com.google.gson.GsonBuilder
import java.time.LocalTime
import timber.log.Timber

/**
 * The newest app log lines, kept in memory so "Send log" on the Webhooks screen can post them
 * (event "debug_log"). Lets a person debug a watch exchange, such as the LifeLog read, without adb.
 */
object DebugLog {
    const val MAX_LINES = 400
    private const val MAX_LINE = 300

    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        synchronized(this) {
            lines.addLast(redact(line).take(MAX_LINE))
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(this) { lines.toList() }

    fun clear() = synchronized(this) { lines.clear() }

    /** The log goes over the network: never let a token through. */
    internal fun redact(line: String): String = line

    fun toJson(watchName: String?, phoneTime: String): String =
        GsonBuilder().disableHtmlEscaping().create().toJson(
            linkedMapOf(
                "event" to "debug_log",
                "watch_name" to watchName,
                "phone_time" to phoneTime,
                "lines" to snapshot(),
            )
        )

    /** Timber tree feeding [DebugLog]. */
    class Tree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                Log.ERROR -> "E"; Log.WARN -> "W"; Log.INFO -> "I"; Log.DEBUG -> "D"; else -> "V"
            }
            add("${LocalTime.now().withNano(0)} $level ${tag ?: ""}: $message" + (t?.let { " | $it" } ?: ""))
        }
    }
}
