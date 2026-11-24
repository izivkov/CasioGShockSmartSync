package org.avmedia.smartsync.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The few building blocks of the app's look the Home Assistant screens use, so the plugin needs no app classes.

@Composable
internal fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = TextStyle.Default,
) {
    // The app's texts keep their size whatever the phone's font scale.
    val scale = LocalDensity.current.fontScale
    Text(
        text = text, modifier = modifier, style = TextStyle(fontSize = fontSize / scale, color = color).merge(style),
        fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign, overflow = overflow, maxLines = maxLines,
    )
}

@Composable
internal fun AppTextLarge(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = TextStyle(fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Start))
}

@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(checked = checked, onCheckedChange = { if (enabled) onCheckedChange(it) }, modifier = modifier, enabled = enabled)
}

@Composable
internal fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.padding(2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(4.dp),
    ) { content() }
}

@Composable
internal fun AppButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape = androidx.compose.foundation.shape.CircleShape) {
        AppText(text = text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun AppIconFromResource(resourceId: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 28.dp) {
    Icon(painter = painterResource(id = resourceId), contentDescription = "", tint = tint, modifier = modifier.size(size))
}

@Composable
internal fun HorizontalSeparator(width: Dp) {
    Box(modifier = Modifier.width(width).fillMaxWidth())
}

internal data class ButtonData(val text: String, val onClick: () -> Unit, val enabled: Boolean = true)

@Composable
internal fun ButtonsRow(buttons: List<ButtonData>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { b -> AppButton(text = b.text, onClick = b.onClick, enabled = b.enabled, modifier = Modifier.padding(vertical = 5.dp)) }
    }
}
