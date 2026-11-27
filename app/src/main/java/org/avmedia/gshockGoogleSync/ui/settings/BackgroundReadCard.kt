package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.health.BackgroundHealthSync
import org.avmedia.gshockGoogleSync.ui.health.HealthReadScheduler

/** "Background health read": how often the LifeLog is read while the watch stays connected (5, 15, 30, 60 min or off; 15 by default). */
@Composable
fun BackgroundReadCard() {
    val context = LocalContext.current
    var minutes by remember { mutableIntStateOf(BackgroundHealthSync.intervalMinutes(context)) }
    BackgroundReadContent(minutes) { minutes = it; BackgroundHealthSync.setIntervalMinutes(context, it) }
}

@Composable
fun BackgroundReadContent(minutes: Int, onChoose: (Int) -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText("Background health read", fontSize = 18.sp)
            AppText(
                if (minutes == 0) "Off. The LifeLog is read only from the Health screen."
                else "Every $minutes minutes while the watch is connected; Home Assistant gets the result.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppText("Interval, in minutes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // One line on every phone: the chips share the width equally and carry only the number (or Off), so the row does
            // not grow with the labels or with the system font size.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HealthReadScheduler.CHOICES_MIN.forEach { m ->
                    FilterChip(
                        selected = m == minutes,
                        onClick = { onChoose(m) },
                        label = {
                            Text(if (m == 0) "Off" else "$m", maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        },
                        modifier = Modifier.weight(1f).semantics { contentDescription = if (m == 0) "Off" else "$m minutes" },
                    )
                }
            }
        }
    }
}