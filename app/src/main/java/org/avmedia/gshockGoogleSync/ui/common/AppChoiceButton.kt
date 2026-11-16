package org.avmedia.gshockGoogleSync.ui.common

import AppText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One option of a "pick one" group:
 *  - selected: filled / highlighted (tapping does nothing),
 *  - selectable: outlined with white (light theme: normal) text,
 *  - [enabled] = false (not available, or a sync is running): greyed out.
 * Colours animate between states; the button squishes slightly when pressed.
 */
@Composable
fun AppChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val normalText = if (isSystemInDarkTheme()) Color.White else cs.onSurface
    val greyed = cs.onSurface.copy(alpha = 0.38f)
    val anim = tween<Color>(220)
    val container by animateColorAsState(
        when {
            selected && enabled -> cs.primary
            selected -> cs.onSurface.copy(alpha = 0.12f)
            else -> Color.Transparent
        }, anim, label = "choiceContainer",
    )
    val content by animateColorAsState(
        when {
            !enabled -> greyed
            selected -> cs.onPrimary
            else -> normalText
        }, anim, label = "choiceContent",
    )
    val border by animateColorAsState(
        when {
            selected && enabled -> cs.primary
            !enabled -> cs.onSurface.copy(alpha = 0.12f)
            else -> cs.outline
        }, anim, label = "choiceBorder",
    )
    val (source, pressMod) = rememberPressScale()
    OutlinedButton(
        onClick = { if (!selected) onClick() },
        modifier = modifier.then(pressMod),
        enabled = enabled,
        shape = CircleShape,
        interactionSource = source,
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container, disabledContentColor = content,
        ),
    ) {
        AppText(text = text, color = content, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}
