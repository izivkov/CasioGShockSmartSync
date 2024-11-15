package org.avmedia.gShockSmartSyncCompose.ui.others

import AppTextLarge
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.avmedia.gShockSmartSyncCompose.R

@Composable
fun WatchName(
    modifier: Modifier,
    watchName: String = stringResource(id = R.string.no_watch),
) {
    AppTextLarge(text = watchName.removePrefix("CASIO").trim(), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchName() {
    WatchName(
        modifier = Modifier
            .padding(start = 0.dp)
    )
}
