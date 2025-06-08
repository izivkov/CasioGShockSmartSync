package org.avmedia.gshockGoogleSync.ui.common

import android.os.Build
import android.widget.NumberPicker
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.avmedia.gshockGoogleSync.R

@Composable
fun ValueSelectionDialog(
    modifier: Modifier = Modifier,
    initialValue: Int,
    range: IntRange,
    step: Int = 1,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    title: String = "Select Value",
    label: String = "Choose a value:",
    unit: String = "",
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    titleTextStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    labelTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    confirmButtonText: String = stringResource(id = R.string.ok),
    dismissButtonText: String = stringResource(id = R.string.cancel),
    spacing: Dp = 16.dp
) {
    val values = range.step(step).toList()
    val initialIndex = values.indexOf(initialValue)
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(values[selectedIndex]) }) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissButtonText)
            }
        },
        title = {
            Text(
                text = title,
                style = titleTextStyle
            )
        },
        text = {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = labelTextStyle
                )
                Spacer(modifier = Modifier.height(spacing))
                NumberPickerView(
                    step = step,
                    pickerValues = values,
                    selectedIndex = selectedIndex,
                    onValueChange = { selectedIndex = it },
                    backgroundColor = backgroundColor,
                    contentColor = contentColor,
                    unit = unit
                )
            }
        }
    )
}

@Composable
fun NumberPickerView(
    step: Int,
    pickerValues: List<Int>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    unit: String = "",
    textSize: TextUnit = 20.sp,
    cornerRadius: Dp = 8.dp,
    padding: Dp = 4.dp,
    wrapSelectorWheel: Boolean = false,
    allowKeyboardInput: Boolean = false
) {
    val safeStep = if (step > 0) step else 1
    require(!(pickerValues.isEmpty() || pickerValues.size < 2)) {
        "pickerValues must have at least 2 elements."
    }

    val adjustedMinValue = 0
    val adjustedMaxValue = (pickerValues.last() - pickerValues.first()) / safeStep
    val safeSelectedIndex = selectedIndex.coerceIn(adjustedMinValue, adjustedMaxValue)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .background(color = backgroundColor, shape = RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = adjustedMinValue
                    maxValue = adjustedMaxValue
                    value = safeSelectedIndex
                    displayedValues = pickerValues.map { "$it $unit" }.toTypedArray()
                    descendantFocusability = if (allowKeyboardInput) {
                        NumberPicker.FOCUS_AFTER_DESCENDANTS
                    } else {
                        NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    }
                    this.wrapSelectorWheel = wrapSelectorWheel

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        this.textColor = contentColor.toArgb()
                        this.textSize = textSize.value
                    }

                    setOnValueChangedListener { _, _, newValue ->
                        onValueChange(pickerValues[newValue])
                    }
                }
            },
            update = { picker ->
                picker.value = safeSelectedIndex
                picker.setOnValueChangedListener { _, _, newValue ->
                    onValueChange(pickerValues[newValue])
                }
            }
        )
    }
}

fun IntRange.step(step: Int): IntProgression = this.first..this.last step step
