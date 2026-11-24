package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** One line or bar series. A null value is a gap: no bar, a broken line. */
data class ChartSeries(val values: List<Float?>, val color: Color)

enum class ChartKind { BARS, LINES }

/** A point of a line chart picked out with a ring and a label above it (the day's highest and lowest heart rate). */
data class ChartMark(val index: Int, val value: Float, val label: String, val color: Color)

/** Where a value falls on the chart: 0 at the bottom, 1 at the top. Shared by the drawing and its test. */
internal fun chartFraction(value: Float, min: Float, max: Float): Float =
    if (max <= min) 0.5f else ((value - min) / (max - min)).coerceIn(0f, 1f)

/** The value range of the series: bars start at 0, lines fit the data with a small margin; null when there is no value. */
internal fun chartRange(series: List<ChartSeries>, kind: ChartKind): Pair<Float, Float>? {
    val all = series.flatMap { it.values }.filterNotNull()
    if (all.isEmpty()) return null
    val hi = all.max()
    val lo = all.min()
    // Lines get a margin, but values that are never below zero never get an axis below zero.
    return if (kind == ChartKind.BARS) 0f to (if (hi <= 0f) 1f else hi) else (if (lo >= 0f) maxOf(0f, lo - 3f) else lo - 3f) to (hi + 3f)
}

/**
 * A small chart on a Compose Canvas (no chart library in the app), with its axes drawn here and nowhere else: a y-axis with 3 to 5
 * round ticks, light grid lines at the ticks, the unit above the axis and [ChartAxes.xLabels] below. Gaps are drawn as a faint
 * tick on the baseline.
 */
@Composable
fun HistoryChart(
    series: List<ChartSeries>,
    kind: ChartKind,
    axes: ChartAxes,
    modifier: Modifier = Modifier,
    grid: Color,
    gapColor: Color,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /** False draws a line without a dot per value (a day of 5-minute values). */
    dots: Boolean = true,
    marks: List<ChartMark> = emptyList(),
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT.dp)) {
        val scale = chartScale(chartRange(series, kind), kind)
        val n = series.maxOfOrNull { it.values.size } ?: 0
        val ticks = scale?.ticks ?: emptyList()
        val tickTexts = ticks.map { axes.yFormat?.invoke(it, scale!!.step) ?: tickText(it, scale!!.step) }
        val tickSizes = tickTexts.map { measurer.measure(it, style).size }
        val unit = measurer.measure(axes.yUnit, style)
        val left = (tickSizes.maxOfOrNull { it.width } ?: 0) + 6f
        val top = unit.size.height + 4f + (tickSizes.maxOfOrNull { it.height } ?: 0) / 2f
        val bottom = if (axes.xLabels.any { it != null }) unit.size.height + 4f else 0f
        val plotW = (size.width - left).coerceAtLeast(1f)
        val plotH = (size.height - top - bottom).coerceAtLeast(1f)
        val base = top + plotH
        fun y(v: Float) = base - chartFraction(v, scale?.low ?: 0f, scale?.high ?: 1f) * plotH
        drawText(measurer, axes.yUnit, Offset(0f, 0f), style)
        ticks.forEachIndexed { i, t ->
            val ty = y(t)
            drawLine(grid, Offset(left, ty), Offset(size.width, ty), strokeWidth = if (i == 0) 2f else 1f)
            drawText(measurer, tickTexts[i], Offset(left - 6f - tickSizes[i].width, ty - tickSizes[i].height / 2f), style)
        }
        if (scale == null) drawLine(grid, Offset(left, base), Offset(size.width, base), strokeWidth = 2f)
        if (n == 0) return@Canvas
        val slot = plotW / n
        fun cx(i: Int) = left + slot * (i + 0.5f)
        for (i in 0 until n) {
            if (series.none { it.values.getOrNull(i) != null }) {
                drawLine(gapColor, Offset(cx(i), base - 6f), Offset(cx(i), base), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
        var lastRight = -1f
        axes.xLabels.take(n).forEachIndexed { i, text ->
            if (text == null) return@forEachIndexed
            val m = measurer.measure(text, style)
            val x = (cx(i) - m.size.width / 2f).coerceIn(left, (size.width - m.size.width).coerceAtLeast(left))
            if (x > lastRight + 4f) {
                drawText(m, topLeft = Offset(x, base + 4f))
                lastRight = x + m.size.width
            }
        }
        if (scale == null) return@Canvas
        if (kind == ChartKind.BARS) {
            val bar = slot * 0.7f / series.size
            series.forEachIndexed { s, ser ->
                ser.values.forEachIndexed { i, v ->
                    if (v != null) {
                        val barTop = y(v).coerceAtMost(base - 2f)
                        drawRect(ser.color, Offset(left + slot * i + slot * 0.15f + bar * s, barTop), Size(bar, base - barTop))
                    }
                }
            }
        } else {
            series.forEach { ser ->
                var prev: Offset? = null
                ser.values.forEachIndexed { i, v ->
                    if (v == null) { prev = null; return@forEachIndexed }
                    val p = Offset(cx(i), y(v))
                    prev?.let { drawLine(ser.color, it, p, strokeWidth = 4f, cap = StrokeCap.Round) }
                    if (dots) drawCircle(ser.color, radius = 4f, center = p)
                    prev = p
                }
            }
            marks.filter { it.index in 0 until n }.forEach { m ->
                val p = Offset(cx(m.index), y(m.value))
                drawCircle(m.color, radius = 7f, center = p, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                val t = measurer.measure(m.label, style.copy(color = m.color))
                val tx = (p.x - t.size.width / 2f).coerceIn(left, (size.width - t.size.width).coerceAtLeast(left))
                val ty = (p.y - 10f - t.size.height).let { if (it < 0f) p.y + 10f else it }
                drawText(t, topLeft = Offset(tx, ty))
            }
        }
    }
}

private const val CHART_HEIGHT = 150
