package org.avmedia.gshockGoogleSync.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.di.ApplicationContextEntryPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(
    modifier: Modifier = Modifier,
    onConfirm: (TimePickerState) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int = 0,
    initialMinute: Int = 0,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    val validInitialHour = remember(initialHour) {
        initialHour.coerceIn(0..23)
    }

    val validInitialMinute = remember(initialMinute) {
        initialMinute.coerceIn(0..59)
    }

    val timePickerState = rememberTimePickerState(
        initialHour = validInitialHour,
        initialMinute = validInitialMinute,
        is24Hour = is24Hour
    )

    Column(
        modifier = modifier
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        TimePicker(
            state = timePickerState,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row {
            AppButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.cancel)
            )

            Spacer(modifier = Modifier.width(8.dp))

            AppButton(
                onClick = { onConfirm(timePickerState) },
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.ok)
            )
        }
    }
}
