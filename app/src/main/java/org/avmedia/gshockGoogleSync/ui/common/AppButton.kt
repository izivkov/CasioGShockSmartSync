package org.avmedia.gshockGoogleSync.ui.common

import AppText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val (source, pressMod) = rememberPressScale()
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.then(pressMod),
        enabled = enabled,
        interactionSource = source,
        shape = androidx.compose.foundation.shape.CircleShape // Capsule shape
    ) {
        AppText(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
