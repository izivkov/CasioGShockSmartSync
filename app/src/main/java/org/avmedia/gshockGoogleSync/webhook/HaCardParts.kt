package org.avmedia.gshockGoogleSync.webhook

import AppText
import AppTextLarge
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppIconFromResource

/** One card of the Home Assistant screen: a title (with an optional icon) and its content. */
@Composable
internal fun HaCard(title: String, iconRes: Int? = null, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                iconRes?.let { AppIconFromResource(resourceId = it) }
                AppTextLarge(text = title)
            }
            content()
        }
    }
}

/** The screen title: the Home Assistant icon and the name. */
@Composable
internal fun HaTitle(text: String, iconRes: Int = R.drawable.ic_home_assistant) {
    Row(
        Modifier.fillMaxWidth().padding(2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconFromResource(resourceId = iconRes)
        AppText(text = "  $text", fontSize = 24.sp)
    }
}

/** A spinner with a short text: the loading state of a card. */
@Composable
internal fun HaLoading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        AppText(text = text, fontSize = 14.sp)
    }
}

/** Plain text in the card: the empty state, or a good ([error] false) or bad result. */
@Composable
internal fun HaNote(text: String, error: Boolean = false) {
    AppText(
        text = text,
        fontSize = 14.sp,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Details": the technical text (codes, last answer), hidden until asked for. Nothing when [text] is blank. */
@Composable
internal fun HaDetails(text: String) {
    if (text.isBlank()) return
    var open by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide details" else "Details") }
        if (open) AppText(text = text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Opens [url] in the browser; false when no app can. */
internal fun openInBrowser(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}
