package org.avmedia.gshockGoogleSync.ui.time

import AppText
import AppTextLarge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import AppTextLink
import org.avmedia.gshockGoogleSync.ui.common.ValueSelectionDialog
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StepCounterView(
    modifier: Modifier = Modifier,
    timeViewModel: TimeViewModel = hiltViewModel()
) {
    val state by timeViewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    AppCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextLarge(
                    text = stringResource(id = R.string.steps),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = stringResource(id = R.string.clear_history),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { timeViewModel.onAction(TimeAction.ClearStepHistory) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = state.selectedStepDataOption.name)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        StepDataOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    timeViewModel.onAction(TimeAction.SetStepDataOption(option))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when (state.selectedStepDataOption) {
                StepDataOption.TODAY -> {
                    val currentSteps = state.stepCounterData.currentDaySteps ?: 0

                    val currentLocale = LocalConfiguration.current.locales[0]
                    val isImperial = currentLocale.country == "US" ||
                            currentLocale.country == "LR" ||
                            currentLocale.country == "MM"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Goal: ${state.stepGoal}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = state.stepGoal.toFloat(),
                                onValueChange = { 
                                    var newValue = it.toInt()
                                    // Make 10,000 steps "sticky"
                                    if (newValue in 9800..10200) {
                                        newValue = 10000
                                    }
                                    timeViewModel.onAction(TimeAction.SetStepGoal(newValue)) 
                                },
                                valueRange = 0f..12000f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val weightUnit = if (isImperial) "lb" else "kg"
                            AppText(
                                text = stringResource(id = R.string.your_weight) + " ($weightUnit)",
                                fontSize = 12.sp
                            )

                            val displayWeight = if (isImperial) (state.weight * 2.20462f / 10f).roundToInt() else (state.weight / 10f).roundToInt()
                            var showWeightDialog by remember { mutableStateOf(false) }

                            AppTextLink(
                                text = "$displayWeight",
                                modifier = Modifier
                                    .clickable { showWeightDialog = true }
                                    .padding(vertical = 4.dp)
                            )

                            if (showWeightDialog) {
                                val range = if (isImperial) 70..550 else 30..250
                                ValueSelectionDialog(
                                    initialValue = displayWeight,
                                    range = range,
                                    onDismiss = { showWeightDialog = false },
                                    onConfirm = { newValue ->
                                        val weight100g = if (isImperial) (newValue / 2.20462f * 10f).roundToInt() else newValue * 10
                                        timeViewModel.onAction(TimeAction.SetWeight(weight100g))
                                        showWeightDialog = false
                                    },
                                    title = stringResource(id = R.string.your_weight),
                                    label = stringResource(id = R.string.your_weight),
                                    unit = " $weightUnit"
                                )
                            }
                        }

                        StepsProgressRing(
                            modifier = Modifier
                                .weight(0.65f)
                                .size(110.dp),
                            currentSteps = currentSteps,
                            goal = state.stepGoal,
                            distanceKm = state.distanceKm,
                            calories = state.calories
                        )
                    }
                }

                StepDataOption.HOURLY -> {
                    val hourly = state.stepCounterData.hourlySteps.filterNotNull().takeLast(6)

                    if (hourly.isEmpty() || hourly.all { it == 0 }) {
                        EmptyHistory()
                    } else {
                        StepsBarChart(values = hourly, labels = hourlyTimeLabels(hourly.size))
                    }
                }

                StepDataOption.DAILY -> {
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
    }
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
            val strokeWidth = 8.dp.toPx()
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
            modifier = Modifier.padding(top = 12.dp)
        ) {
            AppText(text = currentSteps.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "of $goal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.1f km", distanceKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "%.0f kcal", calories),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            .height(110.dp)
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
            .height(110.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No history")
    }
}

private fun hourlyTimeLabels(count: Int): List<String> {
    val calendar = Calendar.getInstance()
    return List(count) { i ->
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.MINUTE, -(count - 1 - i) * 10)
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