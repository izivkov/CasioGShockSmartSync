package org.avmedia.smartsync.plugin.api

import androidx.compose.runtime.Composable

/**
 * A plugin: something the app runs for the watches (a Home Assistant link, a Health Connect writer, a webhook). Found
 * by the host through `META-INF/services/org.avmedia.smartsync.plugin.api.WatchPlugin` (java.util.ServiceLoader), so
 * it needs a public constructor without arguments.
 *
 * Threading: the host calls [activate], [deactivate] and [onEvent] off the main thread, each with a time limit. Events
 * come one at a time and in order, on a queue of this plugin's own: a slow plugin delays only itself. An exception
 * thrown here is caught and counted; after repeated failures the host turns the plugin off and says so in Settings.
 */
interface WatchPlugin {
    /** Stable id, lower case letters, digits and `_` (for example "home_assistant"). Names its settings and storage. */
    val id: String

    /** The name the person sees (Settings, Actions). */
    val name: String

    /** The interface version this plugin was built against: write `override val apiVersion = PluginApi.VERSION`. */
    val apiVersion: String

    /** The plugin's own version, shown in Settings > Plugins ("0.1.0"). */
    val version: String

    /** What the plugin reads, controls, sends where and keeps ([PluginAccess]); the host keeps it to that. */
    val access: PluginAccess

    /** Its icon in Settings, its Actions card and the App log; null: the host's plugin icon. */
    val icon: PluginIcon? get() = null

    /** Starts the plugin. [host] stays valid until [deactivate]. Declare [PluginHost.declareNeeds] here. */
    suspend fun activate(host: PluginHost)

    /** Stops the plugin: end sockets and jobs. Its queued events and commands are dropped by the host. */
    suspend fun deactivate()

    /** One event. Return when it is handled; long work belongs in the plugin's own scope. */
    suspend fun onEvent(event: WatchEvent)

    /** What the plugin shows in the app; null for none. */
    val ui: PluginUi? get() = null
}

/**
 * What a plugin shows in the app. The host lists it in Settings > Plugins (icon, name, version, [statusLine], the on/off
 * switch, a details sheet with its [PluginAccess]; its screen opens from there) and, with [hasActionsRow], in the Actions tab (icon, name, the same switch, and
 * below it [ActionsRow] while the plugin is on). Screens run in the app's theme.
 */
interface PluginUi {
    /** One short line under the name in Settings, for example "Connected" or "Not set up". */
    @Composable
    fun statusLine(): String

    /** The plugin's own screen, full size; [onBack] closes it (the back gesture does too). */
    @Composable
    fun SettingsScreen(onBack: () -> Unit)

    /** True: the plugin has a card in the Actions tab. */
    val hasActionsRow: Boolean get() = false

    /** The plugin's part of its Actions card (what a watch button press does); [onOpenSettings] opens [SettingsScreen]. */
    @Composable
    fun ActionsRow(onOpenSettings: () -> Unit) {}
}

/**
 * What a plugin needs from the host. The host keeps the union over the active plugins: a need of a plugin that is off
 * or failed counts for nothing.
 */
data class PluginNeeds(
    /** Value keys ([WatchValues]) the host should read for this plugin; empty = none, null = every key it knows. */
    val values: Set<String>? = emptySet(),
    /** The plugin wants [WatchEvent.ButtonPressed] (needs [Control.BUTTON_PRESS]): it is listed among the actions a press runs. */
    val buttonPress: Boolean = false,
    /** The plugin uses health data: the host reads the watch's health log in the background as well. */
    val healthReads: Boolean = false,
    /** The plugin needs the link kept up in the background ("Keep connected in background" for that watch). */
    val keepConnected: Boolean = false,
    /** Read every settings code undecoded as well (for decoding work); costs link time on each read. */
    val undecodedSettings: Boolean = false,
)
