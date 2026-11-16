package org.avmedia.gshockGoogleSync.ui.common

import AppText
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * One option of a "pick one" group:
 *  - selected: filled / highlighted (tapping does nothing),
 *  - selectable: outlined with white (light: normal) text,
 *  - [enabled] = false (not available, or a sync is running): greyed out.
 */
@Composable
fun AppChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = @Composable { color: Color ->
        AppText(text = text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
    if (selected) {
        Button(onClick = {}, modifier = modifier, enabled = enabled, shape = CircleShape) {
            label(if (enabled) MaterialTheme.colorScheme.onPrimary else Color.Unspecified)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = CircleShape) {
            val normal = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
            label(if (enabled) normal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}
