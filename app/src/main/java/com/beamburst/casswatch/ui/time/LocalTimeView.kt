package com.beamburst.casswatch.ui.time

import com.beamburst.casswatch.ui.common.AppText
import com.beamburst.casswatch.ui.common.AppTextLarge
import RealTimeClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppCard
import java.util.TimeZone

@Composable
fun LocalTimeView(
    modifier: Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    AppCard(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First Column: Local Time and TimeZoneTextView
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.lg),
            ) {
                AppTextLarge(
                    modifier = Modifier.padding(start = Spacing.sm),
                    text = stringResource(
                        id = R.string.local_time
                    ),
                )

                TextClockComposable(
                    modifier = Modifier.align(Alignment.Start)
                )

                Box {
                    TimeZoneTextView(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = Spacing.sm),
                        textSize = 16.sp
                    )
                }
            }

            // Second Column: Send Time Button
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SendTimeButton(
                    text = stringResource(
                        id = R.string.send_to_watch
                    ),
                    onClick = {
                        timeModel.onAction(TimeAction.SendTimeToWatch)
                    }
                )
            }
        }
    }
}

@Composable
fun TextClockComposable(
    modifier: Modifier = Modifier,
) {
    RealTimeClock(modifier = modifier.padding(start = Spacing.xxs))
}

@Composable
fun TimeZoneTextView(modifier: Modifier = Modifier, textSize: TextUnit) {
    // Use remember to store the time zone ID in a state variable
    var timeZoneId by remember { mutableStateOf(TimeZone.getDefault().id) }

    // LaunchedEffect to monitor changes in the time zone and update the state
    LaunchedEffect(Unit) {
        while (true) {
            val newTimeZoneId = TimeZone.getDefault().id
            timeZoneId = newTimeZoneId

            // Check for changes periodically
            kotlinx.coroutines.delay(1000L) // Adjust the interval as needed
        }
    }

    // Display the text with the current time zone ID
    AppText(
        text = timeZoneId,
        fontSize = textSize,
        modifier = modifier
    )
}

@Composable
fun SendTimeButton(text: String, onClick: () -> Unit) {
    AppButton(
        onClick = {
            onClick()
        },
        text = text
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewLocalTimeCard() {
    LocalTimeView(
        Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
    )
}
