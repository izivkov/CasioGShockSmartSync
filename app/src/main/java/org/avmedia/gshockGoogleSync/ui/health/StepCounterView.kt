package org.avmedia.gshockGoogleSync.ui.health

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
                .padding(16.dp),
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

            val currentSteps = state.stepCounterData.currentDaySteps ?: 0
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                StepsProgressRing(
                    modifier = Modifier.size(160.dp),
                    currentSteps = currentSteps,
                    goal = state.stepGoal,
                    distanceKm = state.distanceKm,
                    calories = state.calories
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // ── HOURLY section ─────────────────────────────────────────
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

            // ── DAILY section ──────────────────────────────────────────
            SectionLabel(text = stringResource(id = R.string.daily))
            Spacer(modifier = Modifier.height(8.dp))

            val dailyHistory = state.stepCounterData.dailyHistory.filterNotNull().takeLast(6)
            val daily = dailyHistory + (state.stepCounterData.currentDaySteps ?: 0)
            if (daily.isEmpty()) {
                EmptyHistory()
            } else {
                StepsBarChart(values = daily, labels = dailyLabels(daily.size))
            }
        }
    }
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
