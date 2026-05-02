package com.beamburst.casswatch.ui.alarms

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextVeryLarge
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.text.format.DateFormat
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dagger.hilt.android.EntryPointAccessors
import com.beamburst.casswatch.R
import com.beamburst.casswatch.di.ApplicationContextEntryPoint
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppTimePicker
import com.beamburst.casswatch.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmItem(
    hours: Int = 12,
    minutes: Int = 0,
    name: String? = null,
    isAlarmEnabled: Boolean = true,
    onToggleAlarm: (Boolean) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    showDaySelector: Boolean = false,
    selectedDays: Set<DayOfWeek> = emptySet(),
    onDayToggled: (DayOfWeek) -> Unit = {}
) {
    var isEnabled by remember { mutableStateOf(isAlarmEnabled) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var alarmHours by remember { mutableIntStateOf(hours) }
    var alarmMinutes by remember { mutableIntStateOf(minutes) }

    val localContext = LocalContext.current.applicationContext
    val appContext = remember {
        EntryPointAccessors.fromApplication(
            localContext,
            ApplicationContextEntryPoint::class.java
        ).getApplicationContext()
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.xs),
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
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTextVeryLarge(
                        text = formatTime(alarmHours, alarmMinutes, appContext),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { showTimePickerDialog = true },
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    if (!name.isNullOrBlank()) {
                        AppText(text = name)
                    }
                }
                AppSwitch(
                    checked = isEnabled,
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

    if (showTimePickerDialog) {
        Dialog(onDismissRequest = { showTimePickerDialog = false }) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(Spacing.lg)
            ) {
                AppTimePicker(
                    onConfirm = { state ->
                        alarmHours = state.hour
                        alarmMinutes = state.minute
                        onTimeChanged(alarmHours, alarmMinutes)
                        showTimePickerDialog = false
                    },
                    onDismiss = { showTimePickerDialog = false },
                    initialHour = alarmHours,
                    initialMinute = alarmMinutes,
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

fun formatTime(hours: Int, minutes: Int, appContext: Context): String {

    fun from0to12(formattedTime: String): String {
        return if (formattedTime.startsWith("0")) {
            "12${formattedTime.substring(1)}"
        } else {
            formattedTime
        }
    }

    val sdf = SimpleDateFormat("H:mm", Locale.getDefault())
    val dateObj: Date = sdf.parse("${hours}:${minutes}")

    val is24HourFormat = DateFormat.is24HourFormat(appContext)
    val timeFormat = if (is24HourFormat) {
        "H:mm"
    } else {
        "K:mm aa"
    }

    val time = SimpleDateFormat(timeFormat, Locale.getDefault()).format(dateObj)
    return if (timeFormat.contains("aa")) from0to12(time) else time
}
