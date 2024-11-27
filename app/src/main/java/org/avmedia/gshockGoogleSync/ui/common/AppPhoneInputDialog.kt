package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AppPhoneInputDialog(
    initialPhoneNumber: String,
    onDismiss: () -> Unit,
    onPhoneNumberValidated: (String) -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    var inputValue by remember { mutableStateOf(initialPhoneNumber.trim()) }
    var validationError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Enter Phone Number") },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputValue.trim(),
                        onValueChange = { inputValue = it.trim() },
                        label = { Text("Phone Number") },
                        isError = validationError,
                        modifier = Modifier.fillMaxWidth(),
                        // visualTransformation = PhoneNumberVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )

                    if (validationError) {
                        Text(
                            text = "Invalid phone number",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                AppButton(text = "OK",
                    onClick = {
                        if (validatePhoneNumber(inputValue)) {
                            onPhoneNumberValidated(inputValue)
                        } else {
                            validationError = true
                        }
                    })
            },
            dismissButton = {
                AppButton(onClick = onDismiss, text = "Cancel")
            }
        )
    }
}

fun validatePhoneNumber(phoneNumber: String): Boolean {
    // val regex = Regex("^\\+?[0-9 ]{10,15}\$")
    val regexInternational = Regex("^\\+?[1-9]\\d{1,14}(\\s*\\(\\d+\\)\\s*|\\s*[-.\\s]?\\d+)*\$")
    return regexInternational.matches(phoneNumber.trim())
}

class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text

        // Format the text into groups of 3-3-4
        val formattedText = buildString {
            var count = 0
            for (char in originalText) {
                if (char.isDigit()) {
                    append(char)
                    count++
                    if (count == 3 || count == 6) {
                        append(' ')
                    }
                }
            }
        }.trimEnd()

        // Offset mapping to account for added spaces
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformedOffset = offset
                if (offset > 3) transformedOffset++
                if (offset > 6) transformedOffset++
                return transformedOffset.coerceAtMost(formattedText.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = offset
                if (offset > 4) originalOffset--
                if (offset > 8) originalOffset--
                return originalOffset.coerceIn(0, originalText.length)
            }
        }

        return TransformedText(
            text = AnnotatedString(formattedText),
            offsetMapping = offsetMapping
        )
    }
}