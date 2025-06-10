package org.avmedia.gshockGoogleSync.ui.common

import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R

@Composable
fun InfoButton(
    infoText: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    dialogTitle: String = stringResource(id = R.string.info),
    confirmButtonText: String = stringResource(id = R.string.ok),
    htmlMode: Int = Html.FROM_HTML_MODE_LEGACY
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = !showDialog },
        modifier = modifier.size(iconSize)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Info Icon",
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .clickable { showDialog = true }
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    Text(
                        text = confirmButtonText,
                        modifier = Modifier.clickable { showDialog = false }
                    )
                },
                title = { Text(text = dialogTitle) },
                text = {
                    Text(text = Html.fromHtml(infoText, htmlMode).toString())
                }
            )
        }
    }
}
