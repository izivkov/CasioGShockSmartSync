package org.avmedia.gshockGoogleSync.history

import com.google.gson.GsonBuilder

/** The whole history as text for the share sheet. Gaps are empty cells in the CSV and null in the JSON, never 0. */
object HistoryExport {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    private val DAY_COLS = listOf("date", "steps", "distance_m", "kcal", "active_min", "walk_sec", "run_sec", "still_sec", "hr_rest", "hr_min", "hr_max", "spo2", "stairs_up", "stairs_down")
    private val HOUR_COLS = listOf("date", "hour", "steps", "hr")
    private val NIGHT_COLS = listOf(
        "date", "score", "recharge_status", "recharge_level", "fell_asleep", "woke_up", "total_min", "deep_min", "light_min",
        "rem_min", "awake_min", "hr", "breathing", "stages",
    )

    /** Three tables in one file, each led by a "# name" line and its header, a blank line between them. */
    fun csv(days: List<DayRow>, hours: List<HourRow>, nights: List<NightRow>): String = buildString {
        table("days", DAY_COLS, days.map { listOf(it.date, it.steps, it.distanceM, it.kcal, it.activeMin, it.walkSec, it.runSec, it.stillSec, it.hrRest, it.hrMin, it.hrMax, it.spo2, it.stairsUp, it.stairsDown) })
        append('\n')
        table("hours", HOUR_COLS, hours.map { listOf(it.date, it.hour, it.steps, it.hr) })
        append('\n')
        table("nights", NIGHT_COLS, nights.map {
            listOf(it.date, it.score, it.rechargeStatus, it.rechargeLevel, it.fellAsleep, it.wokeUp, it.totalMin, it.deepMin, it.lightMin, it.remMin, it.awakeMin, it.hr, it.breathing, it.stages)
        })
    }

    fun json(days: List<DayRow>, hours: List<HourRow>, nights: List<NightRow>): String =
        gson.toJson(linkedMapOf("days" to days, "hours" to hours, "nights" to nights))

    private fun StringBuilder.table(name: String, cols: List<String>, rows: List<List<Any?>>) {
        append("# ").append(name).append('\n').append(cols.joinToString(",")).append('\n')
        rows.forEach { r -> append(r.joinToString(",") { cell(it) }).append('\n') }
    }

    private fun cell(v: Any?): String = when (v) {
        null -> "0"
        is String -> if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        else -> v.toString()
    }
}
