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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.R

@Composable
fun AppPhoneInputDialog(
    initialPhoneNumber: String,
    onDismiss: () -> Unit,
    onPhoneNumberValidated: (String) -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val (inputValue, setInputValue) = remember { mutableStateOf(initialPhoneNumber.trim()) }
    val (validationError, setValidationError) = remember { mutableStateOf(false) }

    val handlePhoneNumberInput = { newValue: String ->
        setInputValue(newValue.trim())
    }

    val handleValidation = {
        if (validatePhoneNumber(inputValue)) {
            onPhoneNumberValidated(inputValue)
        } else {
            setValidationError(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.enter_phone_number),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = handlePhoneNumberInput,
                        label = {
                            Text(
                                text = stringResource(R.string.phone_number),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        isError = validationError,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )

                    if (validationError) {
                        Text(
                            text = "Invalid phone number",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.ok),
                    onClick = handleValidation
                )
            },
            dismissButton = {
                AppButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.cancel)
                )
            }
        )
    }
}

fun validatePhoneNumber(phoneNumber: String): Boolean {
    val regex = Regex("^\\+?[0-9 ()/,*.;\\-N#]*\$|^\$")
    return regex.matches(phoneNumber.trim())
}
