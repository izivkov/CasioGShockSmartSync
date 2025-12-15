package org.avmedia.gshockGoogleSync.ui.time

import AppTextExtraLarge
import AppTextLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard

@Composable
fun TimerView(
    modifier: Modifier = Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    var showTimerDialog by remember { mutableStateOf(false) }

    fun makeLongString(inSeconds: Int): String {
        val hours = inSeconds / 3600
        val minutesAndSeconds = inSeconds % 3600
        val minutes = minutesAndSeconds / 60
        val seconds = minutesAndSeconds % 60

        return "${"%02d".format(hours)}:${"%02d".format(minutes)}:${"%02d".format(seconds)}"
    }

    val handleDismiss: () -> Unit = {
        showTimerDialog = false
    }

    val handleConfirm: (hours: Int, minutes: Int, seconds: Int) -> Unit =
        { hours, minutes, seconds ->
            timeModel.onAction(TimeAction.SetTimer(hours, minutes, seconds))
            showTimerDialog = false
        }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(2f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AppTextLarge(
                    text = stringResource(R.string.timer)
                )
                TimerTimeView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { showTimerDialog = true },
                    timeText = makeLongString(state.timer)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SendTimerButton(
                    modifier = Modifier.padding(0.dp),
                    onClick = {
                        timeModel.onAction(TimeAction.UpdateTimer(state.timer))
                    }
                )
            }
        }
    }

    if (showTimerDialog) {
        Dialog(onDismissRequest = handleDismiss) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(0.dp)
            ) {
                val (hours, minutes, seconds) = convertSecondsToTime(state.timer)
                TimerPicker(
                    hours = hours,
                    minutes = minutes,
                    seconds = seconds,
                    onDismiss = { handleDismiss() },
                    onSubmit = { hr, min, sec -> handleConfirm(hr, min, sec) }
                )
            }
        }
    }
}

@Composable
fun TimerTimeView(modifier: Modifier = Modifier, timeText: String) {
    AppTextExtraLarge(
        text = timeText,
        modifier = modifier,
    )
}

@Composable
fun SendTimerButton(
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    AppButton(
        text = stringResource(
            R.string.send_to_watch
        ),
        onClick = onClick,
        modifier = modifier,
    )
}

fun convertSecondsToTime(totalSeconds: Int): Triple<Int, Int, Int> {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return Triple(hours, minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun PreviewTimerView() {
    TimerView(Modifier)
}