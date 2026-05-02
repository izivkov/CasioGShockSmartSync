package com.beamburst.casswatch.ui.alarms

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextVeryLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    selectedDays: Set<DayOfWeek> = emptySet(),
    onDayToggled: (DayOfWeek) -> Unit = {}
) {
    var isEnabled by remember(isAlarmEnabled) { mutableStateOf(isAlarmEnabled) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xs)
            .clickable { onTap() },
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            if (showDaySelector) {
                DaySelector(
                    selectedDays = selectedDays,
                    onDayToggled = onDayToggled,
                    isEnabled = isEnabled
                )
            }
        }
    }
}

@Composable
private fun DaySelector(
    selectedDays: Set<DayOfWeek>,
    onDayToggled: (DayOfWeek) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selectedDays
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (isEnabled) 1f else 0.4f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable(enabled = isEnabled) { onDayToggled(day) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
