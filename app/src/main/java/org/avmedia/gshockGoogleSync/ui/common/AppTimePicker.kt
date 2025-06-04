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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.di.ApplicationContextEntryPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(
    onConfirm: (TimePickerState) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int,
    initialMinute: Int,
) {
    val localContext = LocalContext.current.applicationContext
    val appContext = remember {
        EntryPointAccessors.fromApplication(
            localContext,
            ApplicationContextEntryPoint::class.java
        ).getApplicationContext()
    }

    val timePickerState = rememberTimePickerState(
        initialHour = if (initialHour in 0..23 && initialMinute in 0..59) initialHour else 0,
        initialMinute = if (initialHour in 0..23 && initialMinute in 0..59) initialMinute else 0,
        is24Hour = DateFormat.is24HourFormat(appContext)
    )

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background) // Theme-based background color
            .padding(16.dp) // Consistent padding
    ) {
        TimePicker(
            state = timePickerState,
            modifier = Modifier.padding(vertical = 16.dp) // Padding around the time picker
        )
        Spacer(modifier = Modifier.height(8.dp)) // Spacing between buttons
        Row {
            AppButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                text = stringResource(
                    id = R.string.cancel
                )
            )
            Spacer(modifier = Modifier.width(8.dp)) // Space between buttons
            AppButton(
                onClick = { onConfirm(timePickerState) },
                modifier = Modifier.weight(1f), // Equal width buttons
                text = stringResource(
                    id = R.string.ok
                )
            )
        }
    }
}
