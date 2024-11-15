package org.avmedia.gShockSmartSyncCompose.ui.common

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun InfoButton(
    infoText: String,
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = !showDialog },
        Modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Info Icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(32.dp)
                .clickable { showDialog = true },
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    Text(
                        text = "OK",
                        modifier = Modifier.clickable {
                            showDialog = false
                        }
                    )
                },
                title = { Text(text = "Info:") },
                text = {
                    Text(text = Html.fromHtml(infoText, Html.FROM_HTML_MODE_LEGACY).toString())
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewInfoButton() {
    InfoButton(
        infoText = "Get your info",
    )
}




