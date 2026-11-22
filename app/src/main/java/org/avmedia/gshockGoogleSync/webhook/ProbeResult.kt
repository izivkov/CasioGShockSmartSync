package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.GsonBuilder

/** One request that was sent (or is still to be sent): the code and every raw notification that came back. */
data class ProbeEntry(val code: Int, val answers: List<String>? = null) {
    val done: Boolean get() = answers != null
}

/** Which one-byte request codes the probe sends, in order. Read requests only. */
object ProbePlan {
    /** 0x28 (battery, temperature) three times, then the other known read requests. */
    val DEFAULT: List<Int> = listOf(0x28, 0x28, 0x28) + listOf(
        0x23, 0x22, 0x10, 0x11, 0x20, 0x3B, 0x3A, 0x26, 0x1D, 0x1E,
        0x13, 0x15, 0x16, 0x18, 0x24, 0x2D, 0x43, 0x45, 0x47,
    )

    /** [DEFAULT] followed by every other code from 0x01 to 0x4F. */
    val ALL: List<Int> = DEFAULT + (0x01..0x4F).filter { it !in DEFAULT }

    fun codes(all: Boolean): List<Int> = if (all) ALL else DEFAULT
    fun hex(code: Int): String = "0x%02X".format(code)
}

/** The whole probe, as shown on the screen and posted to webhooks. */
data class ProbeResult(
    val watchName: String,
    val phoneTime: String,
    val entries: List<ProbeEntry>,
) {
    val complete: Boolean get() = entries.isNotEmpty() && entries.all { it.done }

    /** `{"event":"probe","watch_name":...,"phone_time":...,"complete":...,"results":[{"code":"0x28","answers":[...]}]}` */
    fun toJson(): String {
        val results = entries.filter { it.done }.map {
            linkedMapOf("code" to ProbePlan.hex(it.code), "answers" to it.answers.orEmpty())
        }
        val root = linkedMapOf<String, Any?>(
            "event" to "probe",
            "watch_name" to watchName,
            "phone_time" to phoneTime,
            "complete" to complete,
            "requested" to entries.size,
            "answered" to entries.count { it.done && it.answers.orEmpty().isNotEmpty() },
            "results" to results,
        )
        return GsonBuilder().disableHtmlEscaping().create().toJson(root)
    }
}
