package org.avmedia.gShockSmartSyncCompose.ui.common

import AppText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppTextField(
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String = "",
    fontSize: TextUnit = 20.sp,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    textAlign: TextAlign = TextAlign.End,
) {
    TextField(
        value = value,
        onValueChange = { newText ->
            onValueChange(newText)
        },
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        textStyle = TextStyle(
            fontSize = fontSize,
            lineHeight = fontSize, // Set line height to match font size
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = textAlign
        ),
        singleLine = true,
        placeholder = {
            AppText(
                text = placeholderText,
                style = TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )
        }
    )
}

