package org.avmedia.gshockGoogleSync.history

import java.time.Duration
import java.time.LocalDateTime

/** One stretch of a night in one stage; [minutes] counted from the first stage change. */
data class StageSegment(val stage: String, val startMin: Int, val endMin: Int)

object SleepTimeline {
    /**
     * The segments of a stored timeline ("ISO time,stage" lines). Each stage lasts until the next line; the last one
     * until [wokeUp], or is left out when the end is not known. Lines that cannot be read are skipped.
     */
    fun segments(stages: String?, wokeUp: String?): List<StageSegment> {
        val points = stages.orEmpty().lines().mapNotNull { line ->
            val t = line.substringBefore(',', "").let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val s = line.substringAfter(',', "").trim()
            if (t == null || s.isEmpty()) null else t to s
        }.sortedBy { it.first }
        if (points.isEmpty()) return emptyList()
        val end = wokeUp?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val start = points.first().first
        fun min(t: LocalDateTime) = Duration.between(start, t).toMinutes().toInt()
        return points.mapIndexedNotNull { i, (t, stage) ->
            val next = points.getOrNull(i + 1)?.first ?: end?.takeIf { it > t }
            if (next == null) null else StageSegment(stage, min(t), min(next))
        }.filter { it.endMin > it.startMin }
    }
}
