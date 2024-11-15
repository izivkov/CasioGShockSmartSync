package org.avmedia.gShockSmartSyncCompose.ui.events

import AppSwitch
import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar

@Composable
fun EventItem(
    title: String,
    period: String,
    frequency: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    enabledCount: Int, // Pass in the count of currently enabled events
    maxEnabled: Int = 5 // Set the maximum number of enabled events (default is 5)
) {
    var isEnabled by remember { mutableStateOf(enabled) }

    val maxReminderMessage = stringResource(id = R.string.max_reminders_reached)

    LaunchedEffect(enabled) {
        isEnabled = enabled
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                ) {
                    AppText(
                        text = title,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 0.dp, bottom = 0.dp)
                    )
                    AppSwitch(
                        checked = isEnabled,
                        onCheckedChange = { newValue ->
                            if (newValue && enabledCount >= maxEnabled) {
                                AppSnackbar(maxReminderMessage)
                            } else {
                                onEnabledChange(newValue)
                            }
                        },
                        modifier = Modifier.align(Alignment.Top)
                    )
                }
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp)
                ) {
                    val (periodRef, frequencyRef) = createRefs()

                    AppText(
                        text = period,
                        modifier = Modifier
                            .padding(start = 0.dp)
                            .constrainAs(periodRef) {
                                start.linkTo(parent.start)
                                top.linkTo(parent.top)
                            }
                    )

                    AppText(
                        text = frequency,
                        modifier = Modifier.constrainAs(frequencyRef) {
                            end.linkTo(parent.end)
                            top.linkTo(parent.top)
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAlarmScreen() {
    EventItem(
        title = "Sample Event",
        period = "Feb 22 - Mar 24",
        frequency = "Weekly",
        enabled = false,
        onEnabledChange = {},
        enabledCount = 3
    )
}
