package org.avmedia.gShockSmartSyncCompose.ui.time

import AppText
import WatchTemperature
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.common.AppCard
import org.avmedia.gShockSmartSyncCompose.ui.common.InfoButton
import org.avmedia.gshockapi.WatchInfo

@Composable
fun WatchInfoView(modifier: Modifier) {
    ConstraintLayout(
        modifier = modifier.then(Modifier.fillMaxWidth())
    ) {
        val (watchInfo1, watchInfo2) = createRefs()

        WatchInfoCard1(modifier = Modifier
            .fillMaxWidth()
            .constrainAs(watchInfo1) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(watchInfo2.start)
                width = Dimension.percent(0.5f)
                height = Dimension.fillToConstraints
            })
        WatchInfoCard2(modifier = Modifier.constrainAs(watchInfo2) {
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            start.linkTo(watchInfo1.end)
            end.linkTo(parent.end)
            width = Dimension.percent(0.5f)
        })
    }
}

@Composable
fun WatchInfoCard1(modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(start = 0.dp, top = 0.dp, bottom = 0.dp, end = 0.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(text = stringResource(id = R.string.home_time))  // Home Time Text
                Spacer(modifier = Modifier.width(10.dp))
                InfoButton(infoText = stringResource(id = R.string.info_home_time))  // Info Button
            }

            HomeTime(
                modifier = Modifier.align(Alignment.CenterHorizontally)  // HomeTime composable
            )
        }
    }
}

@Composable
fun WatchInfoCard2(modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(start = 0.dp, top = 0.dp, bottom = 0.dp, end = 0.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Battery()
            }

            WatchTemperature(
                modifier = Modifier.padding(bottom = 0.dp),  // WatchTemperature composable
                hasTemperature = WatchInfo.hasTemperature,
                isNormalButtonPressed = true,
                isConnected = true,
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchInfo() {
    WatchInfoView(Modifier)
}

