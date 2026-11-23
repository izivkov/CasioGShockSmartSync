package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.StepCounterData
import androidx.compose.foundation.layout.width
import org.avmedia.gshockapi.WatchInfo
import AppText
import AppTextLarge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import java.util.Calendar
import java.util.Locale

@Composable
fun StepCounterView(
    modifier: Modifier = Modifier,
    healthViewModel: HealthViewModel = hiltViewModel()
) {
    val state by healthViewModel.state.collectAsState()

    AppCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Card title
            AppTextLarge(
                text = stringResource(id = R.string.steps),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── TODAY section ──────────────────────────────────────────
            SectionLabel(text = stringResource(id = R.string.today))
            Spacer(modifier = Modifier.height(8.dp))

            val dw = WatchInfo.protocol is org.avmedia.gshockapi.protocols.H5600Protocol
            val currentSteps = state.stepCounterData.currentDaySteps ?: 0
            val gauge: @Composable () -> Unit = {
                StepsProgressRing(
                    modifier = Modifier.size(120.dp),
                    currentSteps = currentSteps,
                    goal = state.stepGoal,
                    distanceKm = state.distanceKm,
                    calories = state.stepCounterData.h5600?.energyKcal?.toFloat() ?: state.calories
                )
            }

            if (dw) {
                // DW-H5600: the gauge on the left, the hourly steps on the right, in one panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        gauge()
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel(text = stringResource(id = R.string.hourly))
                        Spacer(modifier = Modifier.height(4.dp))
                        DwHourlyBars(data = state.stepCounterData)
                    }
                }
            } else {
                // ── TODAY section (ABL-100) ──
                SectionLabel(text = stringResource(id = R.string.today))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    gauge()
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // ── HOURLY section (ABL-100) ──
                SectionLabel(text = stringResource(id = R.string.hourly))
                Spacer(modifier = Modifier.height(8.dp))
                val hourly = state.stepCounterData.hourlySteps.filterNotNull().takeLast(10)
                if (hourly.isEmpty() || hourly.all { it == 0 }) {
                    EmptyHistory()
                } else {
                    StepsBarChart(values = hourly, labels = hourlyTimeLabels(hourly.size))
                }

                Spacer(modifier = Modifier.height(16.dp))
                SectionDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // ── DAILY section: shown only when there is history to show ──
                val dailyHistory = state.stepCounterData.dailyHistory.filterNotNull().takeLast(6)
                if (dailyHistory.isNotEmpty()) {
                    val daily = dailyHistory + (state.stepCounterData.currentDaySteps ?: 0)
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionLabel(text = stringResource(id = R.string.daily))
                    Spacer(modifier = Modifier.height(8.dp))
                    StepsBarChart(values = daily, labels = dailyLabels(daily.size))
                }
            }
        }
    }
}

/** DW-H5600: the 24 bars of the day, null = no data (an empty slot, not a 0 bar). Always 24 slots, so one hour is one narrow bar. */
internal fun dwHourlySlots(hourlySteps: List<Int?>): List<Float?> = List(24) { h -> hourlySteps.getOrNull(h)?.toFloat() }

/**
 * DW-H5600: the shared history chart over the fixed 24 hours, x labels every 6 h, a y axis, no value label per bar. Before, the
 * bars ran from the first to the last hour with steps, each labelled, and 24 of them in half the width were 1 px lines with
 * overlapping labels (owner screenshot 2026-11-23 17:53); one hour alone filled the whole width (17:54).
 */
@Composable
private fun DwHourlyBars(data: StepCounterData) {
    if (data.hourlySteps.none { (it ?: 0) > 0 }) {
        EmptyHistory()
        return
    }
    org.avmedia.gshockGoogleSync.ui.health.history.HistoryChart(
        series = listOf(org.avmedia.gshockGoogleSync.ui.health.history.ChartSeries(dwHourlySlots(data.hourlySteps), MaterialTheme.colorScheme.primary)),
        kind = org.avmedia.gshockGoogleSync.ui.health.history.ChartKind.BARS,
        axes = org.avmedia.gshockGoogleSync.ui.health.history.ChartAxes("steps", org.avmedia.gshockGoogleSync.ui.health.history.ChartLabels.hours()),
        grid = MaterialTheme.colorScheme.outlineVariant,
        gapColor = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

@Composable
private fun StepsProgressRing(
    currentSteps: Int,
    goal: Int,
    distanceKm: Float,
    calories: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progress = (currentSteps / goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            drawArc(
                color = trackColor,
                startAngle = -180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = primaryColor,
                startAngle = -180f,
                sweepAngle = 180f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            AppText(text = currentSteps.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "of $goal steps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = String.format(Locale.US, "%.1f km", distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.0f kcal", calories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StepsBarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val maxValue = (values.maxOrNull() ?: 1).coerceAtLeast(1)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val spacing = 8.dp.toPx()
        val totalSpacing = spacing * (values.size - 1)
        val barWidth = (size.width - totalSpacing) / values.size
        val topLabelSpace = 16.dp.toPx()
        val bottomLabelSpace = 18.dp.toPx()
        val chartHeight = size.height - topLabelSpace - bottomLabelSpace

        val paint = android.graphics.Paint().apply {
            color = textColor
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        values.forEachIndexed { index, value ->
            val barHeight = (value.toFloat() / maxValue) * chartHeight
            val left = index * (barWidth + spacing)
            val top = topLabelSpace + (chartHeight - barHeight)

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(value.toString(), left + barWidth / 2f, top - 4.dp.toPx(), paint)
                canvas.nativeCanvas.drawText(labels[index], left + barWidth / 2f, size.height - 2.dp.toPx(), paint)
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No history", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hourlyTimeLabels(count: Int): List<String> {
    val calendar = Calendar.getInstance()
    return List(count) { i ->
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.HOUR_OF_DAY, -(count - 1 - i))
        val hour = calendar.get(Calendar.HOUR)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "a" else "p"
        val displayHour = if (hour == 0) 12 else hour
        "$displayHour$amPm"
    }
}

private fun dailyLabels(count: Int): List<String> {
    val calendar = Calendar.getInstance()
    val dayNames = mapOf(
        Calendar.SUNDAY to "Su",
        Calendar.MONDAY to "Mo",
        Calendar.TUESDAY to "Tu",
        Calendar.WEDNESDAY to "We",
        Calendar.THURSDAY to "Th",
        Calendar.FRIDAY to "Fr",
        Calendar.SATURDAY to "Sa"
    )
    return List(count) { index ->
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_YEAR, index - (count - 1))
        dayNames[calendar.get(Calendar.DAY_OF_WEEK)] ?: ""
    }
}