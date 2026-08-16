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
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager

@Composable
fun HomeTime(
    modifier: Modifier = Modifier,
    defaultText: String = "",
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    val watchFeatureManager = LocalWatchFeatureManager.current
    var text by remember { mutableStateOf(defaultText) }

    LaunchedEffect(state.homeTime) {
        text = withContext(Dispatchers.IO) {
            if (watchFeatureManager.isFeatureSupported("time.world_cities"))
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
