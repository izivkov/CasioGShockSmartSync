package com.beamburst.casswatch.ui.events

import AppSwitch
import com.beamburst.casswatch.ui.common.AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppSnackbar

@Composable
fun EventItem(
    title: String,
    period: String,
    frequency: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    enabledCount: Int,
    modifier: Modifier = Modifier,
    maxEnabled: Int = 5
) {
    val maxReminderMessage = stringResource(id = R.string.max_reminders_reached)

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    AppText(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                    )
                    AppSwitch(
                        checked = enabled,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    AppText(
                        text = period,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    AppText(
                        text = frequency,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
