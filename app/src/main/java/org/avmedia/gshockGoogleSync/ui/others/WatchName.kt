package org.avmedia.gshockGoogleSync.ui.others

import AppTextLarge
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun WatchName(
    modifier: Modifier,
    watchName: String,
) {
    AppTextLarge(text = watchName.removePrefix("CASIO").trim(), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchName() {
    WatchName(
        modifier = Modifier
            .padding(start = 0.dp),
        "GW-5600"
    )
}
