package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.notifications.AppEntry
import org.avmedia.gshockGoogleSync.notifications.InstalledApps
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationSettings
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/** "Phone notifications on the watch": All apps (the default), Only selected apps, or Off. Saved at once. */
@Composable
fun PhoneNotifications() {
    val context = LocalContext.current
    val settings = remember { PhoneNotificationSettings.from(context) }
    var mode by remember { mutableStateOf(settings.mode) }
    var selectedApps by remember { mutableStateOf(settings.selectedApps) }
    var picking by remember { mutableStateOf(false) }

    PhoneNotificationsCard(
        mode = mode,
        selectedCount = selectedApps.size,
        onMode = {
            mode = it
            settings.mode = it
        },
        onChooseApps = { picking = true },
    )

    if (picking) {
        AppPickerDialog(selectedApps) { chosen ->
            selectedApps = chosen
            settings.selectedApps = chosen
            picking = false
        }
    }
}

@Composable
private fun PhoneNotificationsCard(
    mode: PhoneNotificationMode,
    selectedCount: Int,
    onMode: (PhoneNotificationMode) -> Unit,
    onChooseApps: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppTextLarge(text = stringResource(id = R.string.phone_notifications))
            PhoneNotificationMode.entries.forEach { m ->
                ModeRow(m, selected = m == mode, onClick = { onMode(m) })
            }
            AppText(
                text = stringResource(id = infoText(mode)),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mode == PhoneNotificationMode.SELECTED) {
                AppButton(
                    text = stringResource(id = R.string.phone_notifications_choose_apps, selectedCount),
                    onClick = onChooseApps,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedCount == 0) {
                    AppText(
                        text = stringResource(id = R.string.phone_notifications_none_selected),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: PhoneNotificationMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        AppText(
            text = stringResource(id = modeLabel(mode)),
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun modeLabel(mode: PhoneNotificationMode): Int = when (mode) {
    PhoneNotificationMode.ALL -> R.string.phone_notifications_all
    PhoneNotificationMode.SELECTED -> R.string.phone_notifications_selected
    PhoneNotificationMode.OFF -> R.string.phone_notifications_off
}

private fun infoText(mode: PhoneNotificationMode): Int = when (mode) {
    PhoneNotificationMode.ALL -> R.string.phone_notifications_all_info
    PhoneNotificationMode.SELECTED -> R.string.phone_notifications_selected_info
    PhoneNotificationMode.OFF -> R.string.phone_notifications_off_info
}

/** Opens at once; the app list loads off the main thread (once per app session) behind a spinner. */
@Composable
private fun AppPickerDialog(initial: Set<String>, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = InstalledApps.get(context)
    }
    var chosen by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = { onDone(chosen) }) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large
        ) {
            AppPicker(
                apps = apps,
                selected = chosen,
                onToggle = { pkg, on -> chosen = if (on) chosen + pkg else chosen - pkg },
                onDone = { onDone(chosen) }
            )
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<AppEntry>?,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(id = R.string.phone_notifications_search)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (apps == null) {
                LoadingApps()
            } else {
                AppList(InstalledApps.filter(apps, query), selected, onToggle)
            }
        }
        AppButton(
            text = stringResource(id = R.string.done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LoadingApps() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator()
        AppText(text = stringResource(id = R.string.phone_notifications_loading), fontSize = 13.sp)
    }
}

@Composable
private fun AppList(apps: List<AppEntry>, selected: Set<String>, onToggle: (String, Boolean) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = app.packageName in selected,
                    onCheckedChange = { onToggle(app.packageName, it) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    AppText(text = app.label, fontSize = 15.sp)
                    AppText(
                        text = app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
