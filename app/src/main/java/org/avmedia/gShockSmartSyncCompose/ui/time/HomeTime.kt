package org.avmedia.gShockSmartSyncCompose.ui.time

import AppText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.avmedia.gshockapi.WatchInfo

@Composable
fun HomeTime(
    modifier: Modifier = Modifier,
    defaultText: String = "N/A",
    timeModel: TimeViewModel = viewModel()
) {
    val homeTime by timeModel.homeTime.collectAsState()
    var text by remember { mutableStateOf(defaultText) }

    LaunchedEffect(homeTime) {
        text = withContext(Dispatchers.IO) {
            if (WatchInfo.worldCities)
                homeTime
            else defaultText
        }
    }

    AppText(
        text = text,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeTime() {
    HomeTime(Modifier, "America/Toronto")
}
