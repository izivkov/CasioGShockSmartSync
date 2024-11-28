package org.avmedia.gshockGoogleSync.ui.common

import android.os.Build
import android.widget.NumberPicker
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ValueSelectionDialog(
    initialValue: Int,
    range: IntRange,
    step: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    title: String = "Select Value",
    label: String = "Choose a value:",
    unit: String,
) {
    val values = range.step(step).toList()
    val initialIndex = values.indexOf(initialValue)
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(values[selectedIndex]) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label)
                Spacer(modifier = Modifier.height(16.dp))
                NumberPickerView(
                    step = step,
                    pickerValues = values,
                    selectedIndex = selectedIndex,
                    onValueChange = { selectedIndex = it },
                    unit = unit
                )
            }
        }
    )
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun NumberPickerView(
    step: Int,
    pickerValues: List<Int>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    unit: String = ""
) {

    val safeStep = if (step > 0) step else 1

    // Ensure pickerValues are valid
    if (pickerValues.isEmpty() || pickerValues.size < 2) {
        throw IllegalArgumentException("pickerValues must have at least 2 elements.")
    }

    // Calculate adjusted boundaries
    val adjustedMinValue = 0
    val adjustedMaxValue = (pickerValues.last() - pickerValues.first()) / safeStep
    val safeSelectedIndex = selectedIndex.coerceIn(adjustedMinValue, adjustedMaxValue)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = adjustedMinValue
                    maxValue = adjustedMaxValue
                    value = safeSelectedIndex
                    displayedValues = pickerValues.map { "$it $unit" }.toTypedArray()
                    descendantFocusability =
                        NumberPicker.FOCUS_BLOCK_DESCENDANTS // Disable keyboard input
                    wrapSelectorWheel = false

                    // Set listener for value change
                    setOnValueChangedListener { _, _, newValue ->
                        onValueChange(pickerValues[newValue])
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        textColor = contentColor.toArgb()
                    }
                }
            },
            update = { picker ->
                picker.setOnValueChangedListener { _, _, newValue ->
                    onValueChange(newValue) // Pass back the actual value
                }
            }
        )
    }
}

fun IntRange.step(step: Int): IntProgression = this.first..this.last step step
