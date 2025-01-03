package org.avmedia.gshockGoogleSync.ui.others

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppConnectionSpinner
import org.avmedia.gshockGoogleSync.ui.common.InfoButton

@Composable
fun PreConnectionScreen(
    ptrConnectionViewModel: PreConnectionViewModel = viewModel()
) {
    val watchName by ptrConnectionViewModel.watchName.collectAsState()

    val getImageId: (String) -> Int = { name ->
        when {
            "GA" in name || "GMA" in name -> R.drawable.ga_b2100
            "GW" in name || "GMW" in name -> R.drawable.ic_gw_b5600
            "DW" in name || "DMW" in name -> R.drawable.dw_b5600
            "ECB" in name -> R.drawable.ecb_30d
            else -> R.drawable.ic_gw_b5600
        }
    }

    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    ConstraintLayout(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val (watchImageView, connectionSpinner, infoButton) = createRefs()

                        // WatchImageView equivalent
                        Image(
                            painter = painterResource(id = getImageId(watchName)),
                            contentDescription = ptrConnectionViewModel.translateApi.stringResource(
                                context = LocalContext.current,
                                id = R.string.image_of_casio_watch
                            ),
                            modifier = Modifier
                                .constrainAs(watchImageView) {
                                    top.linkTo(parent.top)
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                                .fillMaxSize()
                        )

                        AppConnectionSpinner(modifier = Modifier
                            .constrainAs(connectionSpinner) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            }
                        )

                        Box(modifier = Modifier
                            .padding(top = 30.dp, end = 30.dp)
                            .constrainAs(infoButton) {
                                top.linkTo(parent.top)
                                end.linkTo(parent.end)
                            }) {
                            InfoButton(
                                infoText = ptrConnectionViewModel.translateApi.stringResource(
                                    context = LocalContext.current,
                                    id = R.string.connection_screen_info
                                ) + "v" + BuildConfig.VERSION_NAME
                            )
                        }
                    }
                }

                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ConstraintLayout(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val (watchNamePanel, forgetButton, infoDeviceButton) = createRefs()
                            WatchName(
                                modifier = Modifier
                                    .constrainAs(watchNamePanel) {
                                        start.linkTo(parent.start)
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                    }
                                    .padding(start = 0.dp),

                                watchName = watchName
                            )

                            // ForgetButton equivalent
                            ForgetButton(
                                modifier = Modifier
                                    .padding(0.dp)
                                    .constrainAs(forgetButton) {
                                        end.linkTo(infoDeviceButton.start)
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                    }
                            )

                            Box(modifier = Modifier
                                .padding(end = 4.dp, start = 0.dp)
                                .constrainAs(infoDeviceButton) {
                                    end.linkTo(parent.end)
                                    top.linkTo(parent.top)
                                    bottom.linkTo(parent.bottom)
                                }) {
                                InfoButton(
                                    infoText = ptrConnectionViewModel.translateApi.stringResource(
                                        context = LocalContext.current,
                                        id = R.string.connection_screen_device
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewConnectionScreen() {
    PreConnectionScreen()
}
