package org.avmedia.gshockGoogleSync.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One day of the watch, key = ISO date. A null column is a gap (the watch gave no value), never 0.
 * Walk, run and still are seconds as the watch counts them.
 */
@Entity(tableName = "day")
data class DayRow(
    @PrimaryKey val date: String,
    val steps: Int? = null,
    val distanceM: Int? = null,
    val kcal: Int? = null,
    val activeMin: Int? = null,
    val walkSec: Int? = null,
    val runSec: Int? = null,
    val stillSec: Int? = null,
    val hrRest: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val spo2: Int? = null,
    val stairsUp: Int? = null,
    val stairsDown: Int? = null,
) {
    /**
     * This row (new) laid over [old]: counters that only grow keep the larger value (a partial transfer must not
     * lower a day), the heart-rate extremes keep the extreme, everything else takes the new value, and a gap
     * in the new row keeps the old value.
     */
    fun over(old: DayRow?): DayRow {
        if (old == null) return this
        return DayRow(
            date, steps = big(steps, old.steps), distanceM = big(distanceM, old.distanceM), kcal = big(kcal, old.kcal),
            activeMin = big(activeMin, old.activeMin), walkSec = big(walkSec, old.walkSec), runSec = big(runSec, old.runSec),
            stillSec = big(stillSec, old.stillSec), hrRest = hrRest ?: old.hrRest,
            hrMin = small(hrMin, old.hrMin), hrMax = big(hrMax, old.hrMax), spo2 = spo2 ?: old.spo2,
            stairsUp = stairsUp ?: old.stairsUp, stairsDown = stairsDown ?: old.stairsDown,
        )
    }
}

/** One hour of one day: steps and the heart rate seen in that hour (only where the transfer carried one). */
@Entity(tableName = "hour", primaryKeys = ["date", "hour"])
data class HourRow(
    val date: String,
    val hour: Int,
    val steps: Int? = null,
    val hr: Int? = null,
) {
    fun over(old: HourRow?): HourRow =
        if (old == null) this else HourRow(date, hour, big(steps, old.steps), hr ?: old.hr)
}

/** One heart rate value of section 0x52: [date] and the start of its 2-minute slot as minutes after midnight (watch local time). */
@Entity(tableName = "hr_slot", primaryKeys = ["date", "minute"])
data class HrSlotRow(
    val date: String,
    val minute: Int,
    val bpm: Int,
)

/** One night, key = ISO date the night belongs to. [stages] is the timeline as "ISO time,stage" lines, null = none read. */
@Entity(tableName = "night")
data class NightRow(
    @PrimaryKey val date: String,
    val score: Int? = null,
    val rechargeStatus: Int? = null,
    val rechargeLevel: Int? = null,
    val fellAsleep: String? = null,
    val wokeUp: String? = null,
    val totalMin: Int? = null,
    val deepMin: Int? = null,
    val lightMin: Int? = null,
    val remMin: Int? = null,
    val awakeMin: Int? = null,
    val hr: Int? = null,
    val breathing: Float? = null,
    val stages: String? = null,
) {
    /** The newest read of a night wins field by field; a gap keeps what an earlier read had. */
    fun over(old: NightRow?): NightRow {
        if (old == null) return this
        return NightRow(
            date, score ?: old.score, rechargeStatus ?: old.rechargeStatus, rechargeLevel ?: old.rechargeLevel,
            fellAsleep ?: old.fellAsleep, wokeUp ?: old.wokeUp, totalMin ?: old.totalMin, deepMin ?: old.deepMin,
            lightMin ?: old.lightMin, remMin ?: old.remMin, awakeMin ?: old.awakeMin, hr ?: old.hr,
            breathing ?: old.breathing, stages ?: old.stages,
        )
    }
}

private fun big(a: Int?, b: Int?): Int? = if (a == null) b else if (b == null) a else maxOf(a, b)
private fun small(a: Int?, b: Int?): Int? = if (a == null) b else if (b == null) a else minOf(a, b)
