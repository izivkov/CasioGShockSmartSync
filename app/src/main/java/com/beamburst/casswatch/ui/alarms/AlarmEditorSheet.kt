package com.beamburst.casswatch.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppText
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorSheet(
    draft: AlarmDraft,
    sheetState: SheetState,
    onSave: (hour: Int, minute: Int, name: String, days: Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(draft) { mutableStateOf(draft.name) }
    var selectedDays by remember(draft) { mutableStateOf(draft.days) }
    val timePickerState = rememberTimePickerState(
        initialHour = draft.hour,
        initialMinute = draft.minute,
        is24Hour = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl)
        ) {
            AppText(
                text = stringResource(R.string.alarm_editor_title),
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(Spacing.lg))

            TimePicker(state = timePickerState, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(Spacing.md))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.alarm_editor_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (draft.viewMode == AlarmViewMode.WEEKLY) {
                Spacer(Modifier.height(Spacing.md))
                AppText(
                    text = stringResource(R.string.alarm_editor_repeat),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            },
                            label = {
                                Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)
            ) {
                AppButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier
                )
                AppButton(
                    text = stringResource(R.string.alarm_editor_save),
                    onClick = {
                        onSave(timePickerState.hour, timePickerState.minute, label.trim(), selectedDays)
                    },
                    modifier = Modifier
                )
            }
        }
    }
}
