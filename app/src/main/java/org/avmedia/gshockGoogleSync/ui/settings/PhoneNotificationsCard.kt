package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
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
import org.avmedia.gshockGoogleSync.notifications.AppIcons
import org.avmedia.gshockGoogleSync.notifications.InstalledApps
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.homeassistant.AppStores
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.produceState
import androidx.compose.ui.semantics.Role
import org.avmedia.gshockGoogleSync.notifications.InstalledAppsCache
import org.avmedia.gshockGoogleSync.notifications.NotificationAccess
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode
import org.avmedia.gshockGoogleSync.watch.CurrentWatch

/**
 * "Phone notifications on the watch" for the connected watch: All apps (upstream's behaviour, the default), Only selected
 * apps, or Off. Each watch has its own choice and app list. A watch that cannot show notifications has no such setting.
 */
@Composable
fun PhoneNotificationsCard() {
    val context = LocalContext.current
    val watch = remember { CurrentWatch.key() }
    val supported = remember(watch) { AppStores.haStore(context).notificationsSupported(watch) != false }
    if (watch.isBlank() || !supported) return
    val settings = remember { AppStores.phoneNotifications(context) }
    var mode by remember(watch) { mutableStateOf(settings.mode(watch)) }
    var allowed by remember(watch) { mutableStateOf(settings.allowed(watch)) }
    var picking by remember { mutableStateOf(false) }
    val warning = remember(mode) { NotificationAccess.warning(context, watch) }

    PhoneNotificationsContent(
        mode, allowed.size, { mode = it; settings.setMode(watch, it) }, { picking = true },
        warning = warning, onOpenAccess = { NotificationAccess.openSettings(context) },
    )
    if (picking) {
        // The app list is read off the main thread, once per app session: the picker opens at once with a spinner.
        val apps by produceState<List<AppEntry>?>(initialValue = null) { value = InstalledAppsCache.get(context) }
        Dialog(onDismissRequest = { picking = false }) {
            Surface(Modifier.fillMaxSize().padding(vertical = 24.dp), shape = MaterialTheme.shapes.large) {
                AppPickerContent(apps, allowed, { pkg, on -> settings.setAllowed(watch, pkg, on); allowed = settings.allowed(watch) }, { picking = false }, iconLoader = { AppIcons.load(context, it) })
            }
        }
    }
}

@Composable
fun PhoneNotificationsContent(
    mode: PhoneNotificationMode,
    appCount: Int,
    onMode: (PhoneNotificationMode) -> Unit,
    onChoose: () -> Unit,
    warning: String? = null,
    onOpenAccess: () -> Unit = {},
) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText("Phone notifications on the watch", fontSize = 18.sp)
            PhoneNotificationMode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = m == mode, onClick = { onMode(m) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = m == mode, onClick = null)
                    AppText(m.label, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            AppText(
                when (mode) {
                    PhoneNotificationMode.ALL -> "Every phone notification is sent while the watch is connected."
                    PhoneNotificationMode.SELECTED -> "Only the apps you choose are sent. Ongoing, silent and group summary notifications never are."
                    PhoneNotificationMode.OFF -> "No phone notification is sent to the watch."
                },
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mode == PhoneNotificationMode.SELECTED) {
                AppButton(
                    if (appCount == 0) "Choose apps" else "Choose apps ($appCount chosen)", onChoose, Modifier.fillMaxWidth(),
                )
                if (appCount == 0) AppText("No app is chosen, so nothing is sent yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            warning?.let {
                AppText(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                AppButton("Open notification access", onOpenAccess, Modifier.fillMaxWidth())
            }
            AppText("Home Assistant messages are separate.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The installed apps with a checkbox each and a search field; a spinner while [apps] is still loading (null). */
@Composable
fun AppPickerContent(apps: List<AppEntry>?, selected: Set<String>, onToggle: (String, Boolean) -> Unit, onDone: () -> Unit, initialQuery: String = "", iconLoader: (suspend (String) -> ImageBitmap?)? = null) {
    var query by remember { mutableStateOf(initialQuery) }
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppText("Apps that may send notifications", fontSize = 18.sp)
        OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
        if (apps == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    AppText("Loading apps...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else LazyColumn(Modifier.weight(1f)) {
            items(InstalledApps.filter(apps, query), key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(app.packageName in selected, { onToggle(app.packageName, it) })
                    AppIcon(app.packageName, iconLoader)
                    Column(Modifier.padding(start = 8.dp)) {
                        AppText(app.label, fontSize = 15.sp)
                        AppText(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        AppButton("Done", onDone, Modifier.fillMaxWidth())
    }
}

/** The icon of one app: a grey placeholder of the same size until it is loaded, and when there is none. */
@Composable
private fun AppIcon(packageName: String, loader: (suspend (String) -> ImageBitmap?)?) {
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) { icon = loader?.invoke(packageName) }
    val shape = RoundedCornerShape(8.dp)
    val shown = icon
    if (shown != null) {
        Image(shown, null, Modifier.size(32.dp).clip(shape))
    } else {
        Box(Modifier.size(32.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
