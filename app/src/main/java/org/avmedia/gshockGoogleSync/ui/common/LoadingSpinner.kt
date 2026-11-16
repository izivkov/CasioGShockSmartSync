package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shown in place of a value that has never been read from the watch. */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    CircularProgressIndicator(modifier = modifier.size(size), strokeWidth = 2.dp)
}
