package org.avmedia.gShockSmartSyncCompose.ui.time

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppButton


@Composable
fun TimerPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onDismiss: () -> Unit,
    onSubmit: (Int, Int, Int) -> Unit
) {
    var hourInput by remember {
        mutableStateOf(
            TextFieldValue(
                hours.toString()
                    .padStart(2, '0')
            )
        )
    }
    var minuteInput by remember {
        mutableStateOf(
            TextFieldValue(
                minutes.toString().padStart(2, '0')
            )
        )
    }
    var secondInput by remember {
        mutableStateOf(
            TextFieldValue(
                seconds.toString().padStart(2, '0')
            )
        )
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { AppText(text = stringResource(R.string.enter_timer_time)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Hours input
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { input ->
                            if (input.text == "" || (input.text.length <= 2 && input.text.all { it.isDigit() } && input.text.toIntOrNull() in 0..23)) {
                                hourInput = input.copy(
                                    text = input.text,
                                    selection = TextRange(input.text.length)
                                )
                            }
                        },
                        label = { Text(text = "HH") },
                        placeholder = { Text(text = "HH") },
                        singleLine = true,
                        isError = hourInput.text.toIntOrNull() !in 0..23,
                        modifier = Modifier
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Larger and centered `:` separator
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 2.dp)
                    )

                    // Minutes input
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { input ->
                            if (input.text == "" || (input.text.length <= 2 && input.text.all { it.isDigit() } && input.text.toIntOrNull() in 0..59)) {
                                minuteInput = input.copy(
                                    text = input.text,
                                    selection = TextRange(input.text.length)
                                )
                            }
                        },
                        label = { Text(text = "MM") },
                        placeholder = { Text(text = "MM") },
                        singleLine = true,
                        isError = minuteInput.text.toIntOrNull() !in 0..59,
                        modifier = Modifier
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Larger and centered `:` separator
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(horizontal = 2.dp)
                    )

                    // Seconds input
                    OutlinedTextField(
                        value = secondInput,
                        onValueChange = { input ->
                            if (input.text == "" || (input.text.length <= 2 && input.text.all { it.isDigit() } && input.text.toIntOrNull() in 0..59)) {
                                secondInput = input.copy(
                                    text = input.text,
                                    selection = TextRange(input.text.length)
                                )
                            }
                        },
                        label = { Text(text = "SS") },
                        placeholder = { Text(text = "SS") },
                        singleLine = true,
                        isError = secondInput.text.toIntOrNull() !in 0..59,
                        modifier = Modifier
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 40.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        dismissButton = {
            AppButton(
                onClick = onDismiss,
                text = "Cancel"
            )
        },
        confirmButton = {
            AppButton(
                text = "OK",
                onClick = {
                    val h = hourInput.text.toIntOrNull() ?: 0
                    val m = minuteInput.text.toIntOrNull() ?: 0
                    val s = secondInput.text.toIntOrNull() ?: 0
                    onSubmit(h, m, s)
                },
                enabled = secondInput.text.toIntOrNull() in 0..59 && minuteInput.text.toIntOrNull() in 0..59 && hourInput.text.toIntOrNull() in 0..23
            )
        },
    )
}

@Preview
@Composable
fun PreviewTimePickerDialog() {
    TimerPicker(
        hours = 12,
        minutes = 30,
        seconds = 45,
        onDismiss = { /* Preview dismiss action */ },
        onSubmit = { hours, minutes, seconds ->
            // Handle the submitted time in the preview
            println("Submitted time: $hours:$minutes:$seconds")
        }
    )
}

