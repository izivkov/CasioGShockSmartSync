package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.avmedia.gshockGoogleSync.notifications.AppEntry
import org.avmedia.gshockGoogleSync.notifications.InstalledApps
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.common.AppSwitch
import org.avmedia.gshockGoogleSync.webhook.WebhookStorage

/** "Phone notifications on the watch": a switch (off by default) and the apps allowed to send. */
@Composable
fun PhoneNotificationsCard() {
    val context = LocalContext.current
    val settings = remember { WebhookStorage.phoneNotifications(context) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var allowed by remember { mutableStateOf(settings.allowed) }
    var picking by remember { mutableStateOf(false) }
    val apps = remember(picking) { if (picking) InstalledApps.list(context) else emptyList() }

    PhoneNotificationsContent(enabled, allowed.size, { enabled = it; settings.enabled = it }, { picking = true })
    if (picking) {
        Dialog(onDismissRequest = { picking = false }) {
            Surface(Modifier.fillMaxSize().padding(vertical = 24.dp), shape = MaterialTheme.shapes.large) {
                AppPickerContent(apps, allowed, { pkg, on -> settings.setAllowed(pkg, on); allowed = settings.allowed }, { picking = false })
            }
        }
    }
}

@Composable
fun PhoneNotificationsContent(enabled: Boolean, appCount: Int, onEnabled: (Boolean) -> Unit, onChoose: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("Phone notifications on the watch", fontSize = 18.sp)
                    AppText(
                        if (enabled) "On. Only the apps you choose are sent." else "Off. No phone notification is sent to the watch.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppSwitch(checked = enabled, onCheckedChange = onEnabled)
            }
            if (enabled) {
                AppButton(
                    if (appCount == 0) "Choose apps" else "Choose apps ($appCount chosen)", onChoose, Modifier.fillMaxWidth(),
                )
                if (appCount == 0) AppText("No app is chosen, so nothing is sent yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                AppText(
                    "Ongoing, silent and group summary notifications are never sent. Home Assistant messages are separate.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The installed apps with a checkbox each and a search field. */
@Composable
fun AppPickerContent(apps: List<AppEntry>, selected: Set<String>, onToggle: (String, Boolean) -> Unit, onDone: () -> Unit, initialQuery: String = "") {
    var query by remember { mutableStateOf(initialQuery) }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("Apps that may send notifications", fontSize = 18.sp)
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f)) {
            items(InstalledApps.filter(apps, query), key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(app.packageName in selected, { onToggle(app.packageName, it) })
                    Column {
                        AppText(app.label, fontSize = 15.sp)
                        AppText(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        AppButton("Done", onDone, Modifier.fillMaxWidth())
    }
}
