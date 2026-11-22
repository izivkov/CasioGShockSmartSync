package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** One line or bar series. A null value is a gap: no bar, a broken line. */
data class ChartSeries(val values: List<Float?>, val color: Color)

enum class ChartKind { BARS, LINES }

/** Where a value falls on the chart: 0 at the bottom, 1 at the top. Shared by the drawing and its test. */
internal fun chartFraction(value: Float, min: Float, max: Float): Float =
    if (max <= min) 0.5f else ((value - min) / (max - min)).coerceIn(0f, 1f)

/** The value range of the series: bars start at 0, lines fit the data with a small margin; null when there is no value. */
internal fun chartRange(series: List<ChartSeries>, kind: ChartKind): Pair<Float, Float>? {
    val all = series.flatMap { it.values }.filterNotNull()
    if (all.isEmpty()) return null
    val hi = all.max()
    val lo = all.min()
    return if (kind == ChartKind.BARS) 0f to (if (hi <= 0f) 1f else hi) else (lo - 3f) to (hi + 3f)
}

/** A small chart on a Compose Canvas (no chart library in the app). Gaps are drawn as a faint tick on the baseline. */
@Composable
fun HistoryChart(series: List<ChartSeries>, kind: ChartKind, modifier: Modifier = Modifier, grid: Color, gapColor: Color) {
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        val range = chartRange(series, kind)
        val n = series.maxOfOrNull { it.values.size } ?: 0
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
        if (n == 0) return@Canvas
        val slot = size.width / n
        for (i in 0 until n) {
            if (series.none { it.values.getOrNull(i) != null }) {
                val x = slot * (i + 0.5f)
                drawLine(gapColor, Offset(x, size.height - 6f), Offset(x, size.height), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
        if (range == null) return@Canvas
        fun y(v: Float) = size.height - chartFraction(v, range.first, range.second) * size.height
        if (kind == ChartKind.BARS) {
            val bar = slot * 0.7f / series.size
            series.forEachIndexed { s, ser ->
                ser.values.forEachIndexed { i, v ->
                    if (v != null) {
                        val top = y(v).coerceAtMost(size.height - 2f)
                        drawRect(ser.color, Offset(slot * i + slot * 0.15f + bar * s, top), Size(bar, size.height - top))
                    }
                }
            }
        } else {
            series.forEach { ser ->
                var prev: Offset? = null
                ser.values.forEachIndexed { i, v ->
                    if (v == null) { prev = null; return@forEachIndexed }
                    val p = Offset(slot * (i + 0.5f), y(v))
                    prev?.let { drawLine(ser.color, it, p, strokeWidth = 4f, cap = StrokeCap.Round) }
                    drawCircle(ser.color, radius = 4f, center = p)
                    prev = p
                }
            }
        }
    }
}
