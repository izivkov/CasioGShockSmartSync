package org.avmedia.gShockSmartSyncCompose.ui.others

import AppTextExtraLarge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.avmedia.gShockSmartSyncCompose.R
import org.avmedia.gShockSmartSyncCompose.ui.actions.ActionRunner

@Composable
fun RunActionsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AppTextExtraLarge(
            text = stringResource(id = R.string.running_actions),
            color = (MaterialTheme.colorScheme.primary),
            fontWeight = FontWeight.Bold // Adjust as needed
        )

        ActionRunner(context = LocalContext.current)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewRunActionsScreen() {
    RunActionsScreen()
}
