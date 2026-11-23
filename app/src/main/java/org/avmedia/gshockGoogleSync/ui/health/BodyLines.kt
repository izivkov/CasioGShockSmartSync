package org.avmedia.gshockGoogleSync.ui.health

import java.time.format.DateTimeFormatter
import java.util.Locale
import org.avmedia.gshockapi.model.BodyReadings

/**
 * The Health screen lines for blood oxygen (0x59), cardio load (0x65 today, else the newest 0x5B day) and breathing exercises
 * (0x58). A line only where the watch sent a value; no lines = no card. Pure.
 */
internal object BodyLines {
    private val AT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.getDefault())

    fun lines(body: BodyReadings?): List<Pair<String, String>> {
        if (body == null) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        body.spo2.lastOrNull()?.let { out += "Blood oxygen" to "${it.percent} % (${it.time.format(AT)})" }
        (body.cardioNow ?: body.cardio)?.let { c ->
            val today = body.cardioNow != null
            c.loadRatio?.let { out += "Cardio load ratio" to "%.2f%s".format(Locale.US, it, if (today) " today" else " (${c.date})") }
            c.feedback?.let { out += "Cardio status" to "$it of 4" }
            c.strain?.let { out += "Strain" to "$it" }
            c.tolerance?.let { out += "Tolerance" to "$it" }
        }
        body.breathingSessions.lastOrNull()?.let { s ->
            val n = body.breathingSessions.size
            out += "Breathing exercise" to "${s.time.format(AT)}${if (n > 1) " ($n in this read)" else ""}"
        }
        return out
    }
}
