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
    ): ReadableTest = ReadableTest("", false, emptyList(), "")
}
