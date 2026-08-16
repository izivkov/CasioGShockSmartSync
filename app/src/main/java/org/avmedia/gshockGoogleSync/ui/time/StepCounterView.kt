package org.avmedia.gshockGoogleSync.ui.time

import AppTextLarge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import java.util.Calendar

@Composable
fun StepCounterView(
    modifier: Modifier = Modifier,
    timeViewModel: TimeViewModel = hiltViewModel()
) {
    val state by timeViewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

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
                    val goal = 10000f
                    val progress = (currentSteps / goal).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.height(80.dp).fillMaxWidth()) {
                            val strokeWidth = 8.dp.toPx()
                            val diameter = size.height
                            val topLeftOffset = Offset((size.width - diameter) / 2f, 0f)

                            drawArc(
                                color = surfaceVariantColor,
                                startAngle = -180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = Size(diameter, diameter),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )

                            drawArc(
                                color = primaryColor,
                                startAngle = -180f,
                                sweepAngle = 180f * progress,
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = Size(diameter, diameter),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            AppTextLarge(text = currentSteps.toString())
                            Text(
                                text = "of 10,000 Goal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                StepDataOption.HOURLY -> {
                    val rawHourly = state.stepCounterData.hourlySteps.filterNotNull()
                    // Show the last 6 active intervals (trailing slots are most recent)
                    val hourly = rawHourly.takeLast(6)

                    // Generate labels relative to current time (each slot is 10 mins)
                    val calendar = Calendar.getInstance()
                    val timeLabels = List(hourly.size) { i ->
                        calendar.timeInMillis = System.currentTimeMillis()
                        // Each slot is 10 minutes back
                        calendar.add(Calendar.MINUTE, -(hourly.size - 1 - i) * 10)
                        val hour = calendar.get(Calendar.HOUR)
                        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "a" else "p"
                        val displayHour = if (hour == 0) 12 else hour
                        "$displayHour$amPm"
                    }

                    if (hourly.isEmpty() || hourly.all { it == 0 }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No history")
                        }
                    } else {
                        val maxSteps = (hourly.maxOrNull() ?: 1).coerceAtLeast(1)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val totalBars = hourly.size
                                val spacing = 8.dp.toPx()
                                val totalSpacing = spacing * (totalBars - 1)
                                val barWidth = (size.width - totalSpacing) / totalBars
                                val topLabelSpace = 16.dp.toPx()
                                val bottomLabelSpace = 18.dp.toPx()
                                val chartHeight = size.height - topLabelSpace - bottomLabelSpace

                                val paint = android.graphics.Paint().apply {
                                    color = textColor
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }

                                hourly.forEachIndexed { index, steps ->
                                    val barHeight = (steps.toFloat() / maxSteps) * chartHeight
                                    val left = index * (barWidth + spacing)
                                    val top = topLabelSpace + (chartHeight - barHeight)

                                    // Draw bar
                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = Offset(left, top),
                                        size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )

                                    // Draw step count above bar
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            steps.toString(),
                                            left + (barWidth / 2f),
                                            top - 4.dp.toPx(),
                                            paint
                                        )
                                    }

                                    // Draw time label below bar
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            timeLabels[index],
                                            left + (barWidth / 2f),
                                            size.height - 2.dp.toPx(),
                                            paint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                StepDataOption.DAILY -> {
                    val dailyHistory = state.stepCounterData.dailyHistory.filterNotNull()
                    val todaySteps = state.stepCounterData.currentDaySteps ?: 0
                    // Combine last 6 history days with Today as the final bar
                    val daily = dailyHistory.takeLast(6) + todaySteps

                    if (daily.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No history")
                        }
                    } else {
                        val maxSteps = (daily.maxOrNull() ?: 1).coerceAtLeast(1)

                        // Generate day of week labels ending with today's dayOfWeek
                        val calendar = Calendar.getInstance()
                        val dayLabels = List(daily.size) { index ->
                            // Calculate relative day offsets backwards from current dayOfWeek
                            calendar.timeInMillis = System.currentTimeMillis()
                            calendar.add(Calendar.DAY_OF_YEAR, index - (daily.size - 1))
                            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                                Calendar.SUNDAY -> "Su"
                                Calendar.MONDAY -> "Mo"
                                Calendar.TUESDAY -> "Tu" // (or Calendar.TUESDAY)
                                Calendar.WEDNESDAY -> "We"
                                Calendar.THURSDAY -> "Th"
                                Calendar.FRIDAY -> "Fr"
                                Calendar.SATURDAY -> "Sa"
                                else -> ""
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val totalBars = daily.size
                                val spacing = 6.dp.toPx()
                                val totalSpacing = spacing * (totalBars - 1)
                                val barWidth = (size.width - totalSpacing) / totalBars
                                val topLabelSpace = 16.dp.toPx()
                                val bottomLabelSpace = 18.dp.toPx()
                                val chartHeight = size.height - topLabelSpace - bottomLabelSpace

                                val paint = android.graphics.Paint().apply {
                                    color = textColor
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }

                                daily.forEachIndexed { index, steps ->
                                    val barHeight = (steps.toFloat() / maxSteps) * chartHeight
                                    val left = index * (barWidth + spacing)
                                    val top = topLabelSpace + (chartHeight - barHeight)

                                    // Draw bar
                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = Offset(left, top),
                                        size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )

                                    // Draw step number above bar
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            steps.toString(),
                                            left + (barWidth / 2f),
                                            top - 4.dp.toPx(),
                                            paint
                                        )
                                    }

                                    // Draw Day of Week label below bar
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            dayLabels[index],
                                            left + (barWidth / 2f),
                                            size.height - 2.dp.toPx(),
                                            paint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}