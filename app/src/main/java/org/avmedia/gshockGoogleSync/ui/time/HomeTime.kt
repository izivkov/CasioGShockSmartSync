package org.avmedia.gshockGoogleSync.ui.time

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.avmedia.gshockapi.WatchInfo

@Composable
fun HomeTime(
    modifier: Modifier = Modifier,
    defaultText: String = "N/A",
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    var text by remember { mutableStateOf(defaultText) }

    LaunchedEffect(state.homeTime) {
        text = withContext(Dispatchers.IO) {
            if (WatchInfo.worldCities)
                state.homeTime
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
