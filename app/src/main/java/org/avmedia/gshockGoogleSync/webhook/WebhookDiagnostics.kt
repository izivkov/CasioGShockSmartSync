package org.avmedia.gshockGoogleSync.webhook

/** Turns a failure into text for the Test report and logcat (tag "Webhook"). Never includes URLs or headers. */
object WebhookDiagnostics {
    private const val OUR_PACKAGE = "org.avmedia.gshockGoogleSync"

    /** "ClassName: message" */
    fun oneLine(t: Throwable): String =
        t.javaClass.simpleName + (t.message?.let { ": ${it.take(160)}" } ?: "")

    /** Exception class and message, the first cause, and the top 3 stack frames of our package. */
    fun describe(t: Throwable): String = buildString {
        appendLine(t.javaClass.name + (t.message?.let { ": ${it.take(300)}" } ?: ""))
        t.cause?.let { appendLine("Caused by " + it.javaClass.name + (it.message?.let { m -> ": ${m.take(300)}" } ?: "")) }
        val frames = (t.stackTrace.asSequence() + (t.cause?.stackTrace?.asSequence() ?: emptySequence()))
            .filter { it.className.startsWith(OUR_PACKAGE) }
            .take(3)
        frames.forEach { appendLine("  at ${it.className.removePrefix("$OUR_PACKAGE.")}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
    }.trimEnd()
}
