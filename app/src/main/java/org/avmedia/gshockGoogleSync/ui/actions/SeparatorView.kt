package org.avmedia.gshockGoogleSync.ui.actions

import AppTextLarge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R

@Composable
fun SeparatorView(
    actionsViewModel: ActionsViewModel = hiltViewModel(),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextLarge(
            text = actionsViewModel.translateApi.stringResource(
                context = LocalContext.current,
                id = R.string.emergency_actions
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSeparator() {
    SeparatorView()
}

