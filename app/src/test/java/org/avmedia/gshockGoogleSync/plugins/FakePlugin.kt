package org.avmedia.gshockGoogleSync.plugins

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginUi
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchPlugin

/** A plugin for the app's screen tests: a name, an icon, a status line (as the host shows it) and an Actions line. */
class FakePlugin(
    override val id: String = "fake",
    override val name: String = "Home Assistant",
    val status: String = "Connected",
    private val actionLine: String = "Watch button: Goodnight",
) : WatchPlugin {
    override val apiVersion: String = PluginApi.VERSION
    override val version: String = "0.1.0"
    override val summary: String = "Each watch as a Home Assistant device."
    override val icon: org.avmedia.smartsync.plugin.api.PluginIcon? = org.avmedia.smartsync.plugin.api.PluginIcon.Drawable(org.avmedia.gshockGoogleSync.R.drawable.ic_mdi_gesture_tap_button)
    override val access = org.avmedia.smartsync.plugin.api.PluginAccess(
        reads = setOf(org.avmedia.smartsync.plugin.api.DataUse.STEPS, org.avmedia.smartsync.plugin.api.DataUse.HEART_RATE, org.avmedia.smartsync.plugin.api.DataUse.SLEEP),
        controls = setOf(org.avmedia.smartsync.plugin.api.Control.NOTIFICATIONS, org.avmedia.smartsync.plugin.api.Control.WATCH_SETTINGS),
        sendsTo = listOf("your Home Assistant server"),
        stores = listOf("Home Assistant address and token"),
        androidPermissions = listOf(org.avmedia.smartsync.plugin.api.AndroidPermission("android.permission.POST_NOTIFICATIONS", "its own notifications")),
    )
    override suspend fun activate(host: PluginHost) {}
    override suspend fun deactivate() {}
    override suspend fun onEvent(event: WatchEvent) {}
    override val ui: PluginUi = object : PluginUi {
        @Composable override fun SettingsScreen(onBack: () -> Unit) { Text("The plugin's screen") }
        override val hasActionsRow: Boolean = true
        @Composable override fun ActionsRow(onOpenSettings: () -> Unit) { Text(actionLine) }
    }
}

/** A plugin with nothing but [access] (and a name), for the details tests. */
class FakePluginWith(override val access: org.avmedia.smartsync.plugin.api.PluginAccess) : WatchPlugin {
    override val id: String = "with"
    override val name: String = "With"
    override val apiVersion: String = PluginApi.VERSION
    override val version: String = "1"
    override suspend fun activate(host: PluginHost) {}
    override suspend fun deactivate() {}
    override suspend fun onEvent(event: WatchEvent) {}
}
