package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import AppText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * What the shared chart draws around the data: the unit above the y-axis, how a tick value is written, and one optional label
 * per slot on the x-axis (null = no label there). One description for every chart, so every chart gets the same axes.
 */
data class ChartAxes(
    val yUnit: String,
    val xLabels: List<String?> = emptyList(),
    val yFormat: ((Float, Float) -> String)? = null,
)

/** The y-axis of a chart: the snapped range [low] to [high] and the tick values at every [step]. */
data class ChartScale(val low: Float, val high: Float, val step: Float) {
    val ticks: List<Float> get() = chartTicks(low, high, step)
}

/** The smallest 1, 2 or 5 times a power of ten so that [range] holds at most [maxIntervals] steps (3 to 5 ticks). */
internal fun niceStep(range: Float, maxIntervals: Int = 4): Float {
    if (range <= 0f || !range.isFinite()) return 1f
    var power = floor(Math.log10(range.toDouble() / maxIntervals)).toInt() - 1
    while (true) {
        for (m in listOf(1f, 2f, 5f)) {
            val step = (m * 10.0.pow(power)).toFloat()
            if (ceil(range / step - 1e-4f) <= maxIntervals) return step
        }
        power++
    }
}

/** The round axis for data between [min] and [max]: bars start at 0, lines snap outwards to the step. Null for no data. */
internal fun chartScale(range: Pair<Float, Float>?, kind: ChartKind): ChartScale? {
    range ?: return null
    val lo = if (kind == ChartKind.BARS) 0f else range.first
    val hi = if (range.second <= lo) lo + 1f else range.second
    val step = niceStep(hi - lo)
    val low = if (kind == ChartKind.BARS) 0f else floor(lo / step) * step
    val high = ceil(hi / step - 1e-4f) * step
    return ChartScale(low, if (high <= low) low + step else high, step)
}

internal fun chartTicks(low: Float, high: Float, step: Float): List<Float> {
    if (step <= 0f || high < low) return emptyList()
    val count = Math.round((high - low) / step)
    return List(count + 1) { low + it * step }
}

/** A tick value written without a needless decimal: 60, 2.5, 1500. */
internal fun tickText(value: Float, step: Float): String =
    if (step >= 1f || abs(value - Math.round(value)) < 1e-4f) Math.round(value).toString() else String.format(Locale.US, "%.1f", value)

/** A pace in seconds per km as m:ss. */
internal fun paceText(seconds: Float): String = "%d:%02d".format(Math.round(seconds) / 60, Math.round(seconds) % 60)

/** The x labels of the day charts. */
object ChartLabels {
    /** Seven days: the weekday of every day. More: a date every [every] days, counting back from today. */
    fun days(today: LocalDate, n: Int, locale: Locale = Locale.getDefault(), every: Int = 5): List<String?> =
        List(n) { i ->
            val date = today.minusDays((n - 1 - i).toLong())
            when {
                n <= 7 -> date.format(DateTimeFormatter.ofPattern("EEE", locale))
                (n - 1 - i) % every == 0 -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
                else -> null
            }
        }

    /** 24 hourly slots: 0h, 6h, 12h and 18h. */
    fun hours(slots: Int = 24, every: Int = 6): List<String?> = List(slots) { if (it % every == 0) "${it}h" else null }

    /** Four labels spread over a workout: minutes when it lasted less than an hour, else h:mm. Needs the elapsed seconds of each slot. */
    fun elapsed(elapsedSec: List<Int?>): List<String?> {
        val total = elapsedSec.filterNotNull().maxOrNull() ?: return emptyList()
        val n = elapsedSec.size
        if (n < 2) return List(n) { null }
        val at = setOf(0, n / 3, 2 * n / 3, n - 1)
        return List(n) { i ->
            val s = elapsedSec[i]
            if (i !in at || s == null) null else if (total < 3600) "${s / 60} min" else "%d:%02d".format(s / 3600, s % 3600 / 60)
        }
    }
}

/** A colour dot and a word for each series, in one row (the heart rate chart: resting and max). */
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                AppText(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
