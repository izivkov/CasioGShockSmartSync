package org.avmedia.gshockGoogleSync.webhook

/** A Test result in plain words. [details] (status code, the server's answer) is shown only on request. */
data class ReadableTest(val headline: String, val ok: Boolean, val notes: List<String>, val details: String)

object TestResultText {
    fun of(
        status: Int?,
        message: String,
        unknown: List<String> = emptyList(),
        failed: List<String> = emptyList(),
        missing: List<String> = emptyList(),
        watchConnected: Boolean = true,
    ): ReadableTest {
        val headline = when {
            status == null -> "No answer. Is the address right and the phone online?"
            status in 200..299 -> "It worked. The server accepted it."
            status == 401 || status == 403 -> "The server did not accept the login or token."
            status == 404 -> "The server does not know this address."
            status in 400..499 -> "The server refused it. Check the address and the body."
            status >= 500 -> "The server had a problem. Try again later."
            else -> "The server answered in an unexpected way."
        }
        val notes = buildList {
            if (unknown.isNotEmpty()) add("Not a known variable: ${unknown.joinToString()}")
            if (failed.isNotEmpty()) add("Could not read: ${failed.joinToString()}")
            if (missing.isNotEmpty()) add("No value yet for: ${missing.joinToString()}")
            if (!watchConnected) add("The watch is not connected, so watch values are empty.")
        }
        val details = buildString {
            appendLine(if (status != null) "HTTP $status" else "No response")
            if (message.isNotEmpty()) appendLine(message)
        }.trimEnd()
        return ReadableTest(headline, status != null && status in 200..299, notes, details)
    }
}
