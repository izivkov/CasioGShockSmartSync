package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.di.TranslateEntryPoint

data class ButtonData(val text: String, val onClick: () -> Unit)

@Composable
fun ButtonsRow(
    buttons: List<ButtonData>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { buttonData ->
            AppButton(
                text = buttonData.text,
                onClick = buttonData.onClick,
                modifier = Modifier
                    .padding(top = 5.dp, bottom = 5.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewButtonRow() {

    val translateApi = EntryPointAccessors.fromApplication(
        LocalContext.current,
        TranslateEntryPoint::class.java
    ).getTranslateRepository()

    val buttons = listOf(
        ButtonData(
            text = translateApi.stringResource(
                context = LocalContext.current,
                id = R.string.send_alarms_to_phone
            ),
            onClick = { /* handle click */ }),
        ButtonData(
            text = translateApi.stringResource(
                context = LocalContext.current,
                id = R.string.send_alarms_to_watch
            ),
            onClick = { /* handle click */ })
    )

    ButtonsRow(buttons = buttons)
}
