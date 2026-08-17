package org.avmedia.gshockGoogleSync.ui.time

import AppText
import WatchTemperature
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.InfoButton
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager

@Composable
fun WatchInfoView(modifier: Modifier) {
    val watchFeatureManager = LocalWatchFeatureManager.current
    val isHomeTimeSupported = watchFeatureManager.isCardSupported("home_time_card")
    val isBatteryTemperatureSupported = watchFeatureManager.isCardSupported("battery_temperature_card")

    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (isHomeTimeSupported) {
            WatchInfoCard1(
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (isBatteryTemperatureSupported) {
            WatchInfoCard2(
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
fun WatchInfoCard1(
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = stringResource(id = R.string.home_time)
                )
                Spacer(modifier = Modifier.width(6.dp))
                InfoButton(
                    infoText = stringResource(id = R.string.info_home_time)
                )
            }

            HomeTime(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun WatchInfoCard2(modifier: Modifier = Modifier) {
    val watchFeatureManager = LocalWatchFeatureManager.current

    AppCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Battery()
            }

            WatchTemperature(
                hasTemperature = watchFeatureManager.isFeatureSupported("time.temperature"),
                isNormalButtonPressed = true,
                isConnected = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchInfo() {
    WatchInfoView(Modifier)
}
