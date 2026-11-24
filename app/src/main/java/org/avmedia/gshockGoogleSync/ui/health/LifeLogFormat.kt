package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.BodyReadings
import org.avmedia.gshockapi.model.CardioStatus
import org.avmedia.gshockapi.model.Spo2Reading
import org.avmedia.gshockapi.model.StairsRecord
import java.util.Locale

/** Minutes as hours and minutes, for example 454 is "7:34". */
internal fun formatMinutes(minutes: Int): String = String.format(Locale.US, "%d:%02d", minutes / 60, minutes % 60)

/** The newest blood oxygen reading and the range and number of all readings of a read. */
internal data class Spo2Summary(
    val latest: Spo2Reading,
    val min: Int,
    val max: Int,
    val count: Int,
)

/** Null without readings. */
internal fun spo2Summary(readings: List<Spo2Reading>): Spo2Summary? {
    val latest = readings.maxByOrNull { it.time } ?: return null
    return Spo2Summary(latest, readings.minOf { it.percent }, readings.maxOf { it.percent }, readings.size)
}

/** Floors up and down summed over the day of the newest stairs record; null without records. */
internal fun stairsOfLatestDay(stairs: List<StairsRecord>): Pair<Int, Int>? {
    val day = stairs.maxByOrNull { it.time }?.time?.toLocalDate() ?: return null
    val ofDay = stairs.filter { it.time.toLocalDate() == day }
    return ofDay.sumOf { it.up } to ofDay.sumOf { it.down }
}

/** Today's cardio load when the watch gave one, otherwise the newest cardio status record. */
internal fun cardioOf(body: BodyReadings): CardioStatus? = body.cardioNow ?: body.cardio
