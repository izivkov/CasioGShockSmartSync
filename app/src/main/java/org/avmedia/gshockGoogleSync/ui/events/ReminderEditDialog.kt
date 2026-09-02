package org.avmedia.gshockGoogleSync.ui.events

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.EventDate
import org.avmedia.gshockapi.model.RepeatPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditDialog(
    event: Event,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit
) {
    // Robust reflection to access private fields in Event class
    val eStartDate = remember(event) { event.getPrivateField<EventDate>("startDate") }
    val eEndDate = remember(event) { event.getPrivateField<EventDate>("endDate") }
    val eRepeatPeriod = remember(event) { event.getPrivateField<RepeatPeriod>("repeatPeriod") ?: RepeatPeriod.NEVER }
    val eDaysOfWeek = remember(event) { event.getPrivateField<List<DayOfWeek>>("daysOfWeek") }

    var title by remember(event) { mutableStateOf(event.title) }
    var startDate by remember(event) {
        mutableStateOf(
            eStartDate?.let {
                LocalDate.of(it.year, it.month, it.day)
            } ?: LocalDate.now()
        )
    }
    var repeatPeriod by remember(event) { mutableStateOf(eRepeatPeriod) }
    var daysOfWeek by remember(event) { mutableStateOf(eDaysOfWeek ?: emptyList()) }
    var endDate by remember(event) {
        mutableStateOf(
            eEndDate?.let {
                LocalDate.of(it.year, it.month, it.day)
            } ?: startDate.plusYears(1)
        )
    }

    var hasEndDate by remember(event) {
        mutableStateOf(
            eEndDate != null && !eEndDate.equals(eStartDate ?: EventDate(0, java.time.Month.JANUARY, 1))
        )
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Event") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { newValue ->
                        title = EventUtils.sanitizeEventTitle(newValue)
                    },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Start Date")
                SimpleDatePicker(
                    date = startDate,
                    minDate = LocalDate.now(),
                    onDateChange = { 
                        startDate = it
                        if (startDate.isAfter(endDate)) {
                            endDate = startDate
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = repeatPeriod.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        RepeatPeriod.entries.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    repeatPeriod = period
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (repeatPeriod == RepeatPeriod.WEEKLY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Repeat on")
                    DayOfWeek.entries.forEach { day ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = daysOfWeek.contains(day),
                                onCheckedChange = { checked ->
                                    daysOfWeek = if (checked) {
                                        daysOfWeek + day
                                    } else {
                                        daysOfWeek - day
                                    }
                                }
                            )
                            Text(day.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                if (repeatPeriod != RepeatPeriod.NEVER) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hasEndDate,
                            onCheckedChange = { hasEndDate = it }
                        )
                        Text("Has End Date")
                    }

                    if (hasEndDate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("End Date")
                        SimpleDatePicker(
                            date = endDate,
                            minDate = startDate,
                            onDateChange = { endDate = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedEvent = Event(
                    if (title.isBlank()) "No Title" else title,
                    EventDate(startDate.year, startDate.month, startDate.dayOfMonth),
                    if (repeatPeriod == RepeatPeriod.NEVER || !hasEndDate) null else EventDate(endDate.year, endDate.month, endDate.dayOfMonth),
                    repeatPeriod,
                    if (repeatPeriod == RepeatPeriod.WEEKLY) daysOfWeek else null,
                    event.enabled,
                    event.incompatible
                )
                onSave(updatedEvent)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePicker(
    date: LocalDate,
    minDate: LocalDate? = null,
    onDateChange: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val dialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                )
                minDate?.let {
                    dialog.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                dialog.show()
            }
    ) {
        OutlinedTextField(
            value = "${date.year}-${date.monthValue}-${date.dayOfMonth}",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private fun <T> Any.getPrivateField(name: String): T? {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        try {
            val field = currentClass.getDeclaredField(name)
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            return field.get(this) as? T
        } catch (_: NoSuchFieldException) {
            currentClass = currentClass.superclass
        } catch (e: Exception) {
            Timber.e(e, "Error accessing field $name")
            break
        }
    }
    return null
}
