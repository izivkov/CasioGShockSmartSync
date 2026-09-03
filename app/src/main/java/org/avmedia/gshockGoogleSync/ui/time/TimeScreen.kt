package org.avmedia.gshockGoogleSync.ui.time

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import AppText
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

@Composable
fun TimeScreen(timeViewModel: TimeViewModel = hiltViewModel()) {
    val state by timeViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        timeViewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    AppSnackbar(event.message)
                }
            }
        }
    }

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ConstraintLayout {
                val (title, localTime, timer, watchName, watchInfo, voiceCard) = createRefs()

                ScreenTitle(
                        stringResource(id = R.string.time),
                        Modifier.constrainAs(title) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                )

                LocalTimeView(
                        Modifier.fillMaxWidth()
                                .padding(vertical = 0.dp) // Adjust padding as needed
                                .constrainAs(localTime) {
                                    top.linkTo(title.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                )

                val watchFeatureManager = LocalWatchFeatureManager.current
                val isStepCounterSupported = watchFeatureManager.isCardSupported("step_counter_card")

                TimerView(
                        modifier =
                                Modifier.fillMaxWidth().constrainAs(timer) {
                                    top.linkTo(localTime.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                )

                val stepCounter = createRef()
                if (isStepCounterSupported) {
                    StepCounterView(
                        modifier = Modifier.fillMaxWidth()
                            .constrainAs(stepCounter) {
                                top.linkTo(timer.bottom)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            }
                    )
                }

                WatchNameView(
                        modifier =
                                Modifier.fillMaxWidth().constrainAs(watchName) {
                                    top.linkTo(if (isStepCounterSupported) stepCounter.bottom else timer.bottom)
                                    bottom.linkTo(if (state.isVoiceCommandSupported) voiceCard.top else watchInfo.top)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                    height = Dimension.fillToConstraints
                                }
                )

                WatchInfoView(
                        modifier =
                                Modifier.fillMaxWidth().constrainAs(watchInfo) {
                                    bottom.linkTo(parent.bottom)
                                    start.linkTo(parent.start)
                                    end.linkTo(parent.end)
                                }
                )

                if (state.isVoiceCommandSupported) {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .constrainAs(voiceCard) {
                                bottom.linkTo(watchInfo.top)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            }
                            .clickable { timeViewModel.onAction(TimeAction.StartVoiceCommand) },
                        containerColor = if (state.isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.isListening) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = R.drawable.voice_assist),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (state.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            AppText(
                                text = stringResource(id = R.string.voice_command),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTimeScreen() {
    TimeScreen()
}
