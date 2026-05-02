package com.beamburst.casswatch.ui.time

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.theme.Spacing
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppText
import org.avmedia.gshockapi.WatchInfo

@Composable
fun WatchSummaryCard(
    modifier: Modifier = Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    var showWatchManager by remember { mutableStateOf(false) }
    val noWatch = stringResource(R.string.no_watch)
    val watchName = state.watchName.ifBlank { noWatch }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = watchName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AppText(
                        text = if (state.isConnected) "Connected" else "Not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusPill(connected = state.isConnected)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (WatchInfo.hasHomeTime) {
                    SummaryMetric(
                        label = stringResource(R.string.home_time),
                        value = state.homeTime.ifBlank { "N/A" },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (WatchInfo.hasBatteryLevel) {
                    BatteryMetric(
                        batteryLevel = state.batteryLevel,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (WatchInfo.hasTemperature) {
                    SummaryMetric(
                        label = "Temp",
                        value = if (state.isConnected) "${state.temperature} C" else "N/A",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AppButton(
                text = stringResource(R.string.manage_watches),
                onClick = { showWatchManager = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showWatchManager) {
        WatchConnectionDialog(onDismiss = { showWatchManager = false })
    }
}

@Composable
private fun StatusPill(connected: Boolean) {
    val containerColor =
        if (connected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        if (connected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        AppText(
            text = if (connected) "Live" else "Offline",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            AppText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppText(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BatteryMetric(
    batteryLevel: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            AppText(
                text = "Battery",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = "$batteryLevel%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                BatteryGlyph(batteryLevel = batteryLevel)
            }
        }
    }
}

@Composable
private fun BatteryGlyph(batteryLevel: Int) {
    val outline = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(width = Spacing.xxl, height = Spacing.lg)) {
        val capWidth = size.width * 0.08f
        val bodyWidth = size.width - capWidth - 2f
        val bodyHeight = size.height * 0.72f
        val bodyTop = (size.height - bodyHeight) / 2f
        val radius = bodyHeight * 0.18f
        val fillInset = 3f
        val fillWidth = (bodyWidth - fillInset * 2) * (batteryLevel.coerceIn(0, 100) / 100f)

        drawRoundRect(
            color = outline,
            topLeft = androidx.compose.ui.geometry.Offset(0f, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 2f)
        )
        drawRoundRect(
            color = fill,
            topLeft = androidx.compose.ui.geometry.Offset(fillInset, bodyTop + fillInset),
            size = Size(fillWidth, bodyHeight - fillInset * 2),
            cornerRadius = CornerRadius(radius / 2, radius / 2)
        )
        drawRoundRect(
            color = outline,
            topLeft = androidx.compose.ui.geometry.Offset(bodyWidth + 1f, bodyTop + bodyHeight * 0.3f),
            size = Size(capWidth, bodyHeight * 0.4f),
            cornerRadius = CornerRadius(radius / 2, radius / 2)
        )
    }
}
