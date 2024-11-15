package org.avmedia.gShockSmartSyncCompose.ui.time

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.theme.GShockSmartSyncTheme
import org.avmedia.gShockSmartSyncCompose.ui.common.ScreenTitle

@Composable
fun TimeScreen(navController: NavController) {
    GShockSmartSyncTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ConstraintLayout {

                val (title, localTime, timer, watchName, watchInfo) = createRefs()

                ScreenTitle(stringResource(id = R.string.time), Modifier
                    .constrainAs(title) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })

                LocalTimeView(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp) // Adjust padding as needed
                        .constrainAs(localTime) {
                            top.linkTo(title.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        })

                TimerView(modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(timer) {
                        top.linkTo(localTime.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })

                WatchNameView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .constrainAs(watchName) {
                            top.linkTo(timer.bottom)
                            bottom.linkTo(watchInfo.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            height = Dimension.fillToConstraints
                        }
                )

                WatchInfoView(modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(watchInfo) {
                        top.linkTo(watchName.bottom)
                        bottom.linkTo(parent.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTimeScreen() {
    TimeScreen(navController = rememberNavController())
}
