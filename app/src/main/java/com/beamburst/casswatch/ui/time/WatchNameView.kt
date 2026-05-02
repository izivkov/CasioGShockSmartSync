package com.beamburst.casswatch.ui.time

import com.beamburst.casswatch.theme.Spacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.beamburst.casswatch.R
import com.beamburst.casswatch.ui.common.AppButton
import com.beamburst.casswatch.ui.common.AppCard
import com.beamburst.casswatch.ui.common.AppTextVeryLarge

@Composable
fun WatchNameView(
    modifier: Modifier = Modifier,
    timeModel: TimeViewModel = hiltViewModel()
) {
    val state by timeModel.state.collectAsState()
    var showWatchManager by remember { mutableStateOf(false) }

    AppCard(
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WatchName(text = state.watchName)
                AppButton(
                    text = stringResource(R.string.manage_watches),
                    onClick = { showWatchManager = true },
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            }
        }
    }

    if (showWatchManager) {
        WatchConnectionDialog(onDismiss = { showWatchManager = false })
    }
}

@Composable
fun WatchName(
    modifier: Modifier = Modifier,
    text: String
) {
    AppTextVeryLarge(
        text = text,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewWatchName() {
    WatchNameView(Modifier)
}
