package com.beamburst.casswatch.ui.alarms

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextVeryLarge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmItem(
    hours: Int = 12,
    minutes: Int = 0,
    name: String? = null,
    isAlarmEnabled: Boolean = true,
    showAsEnabled: Boolean = isAlarmEnabled,
    onToggleAlarm: (Boolean) -> Unit,
    onTap: () -> Unit,
    showDaySelector: Boolean = false,
    selectedDays: Set<DayOfWeek> = emptySet()
) {
    var isEnabled by remember(isAlarmEnabled) { mutableStateOf(isAlarmEnabled) }
    val isWeekly = showDaySelector

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .clickable { onTap() },
    ) {
        if (isWeekly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (!name.isNullOrBlank()) {
                        AppText(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppTextVeryLarge(
                        text = String.format(Locale.US, "%02d:%02d", hours, minutes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeekdayTextRow(
                        selectedDays = selectedDays,
                        enabled = isEnabled
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    AppSwitch(
                        checked = showAsEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            onToggleAlarm(checked)
                        },
                        modifier = Modifier.graphicsLayer(scaleX = 0.82f, scaleY = 0.82f)
                    )
                }
            }
        } else {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                )
                {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTextVeryLarge(
                            text = String.format(Locale.US, "%02d:%02d", hours, minutes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!name.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            AppText(text = name)
                        }
                    }
                    AppSwitch(
                        checked = showAsEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            onToggleAlarm(checked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayTextRow(
    selectedDays: Set<DayOfWeek>,
    enabled: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selectedDays
            val baseColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                color = when {
                    enabled -> baseColor
                    isSelected -> baseColor.copy(alpha = 0.8f)
                    else -> baseColor.copy(alpha = 0.45f)
                },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (isSelected) TextDecoration.Underline else TextDecoration.None
                )
            )
        }
    }
}
