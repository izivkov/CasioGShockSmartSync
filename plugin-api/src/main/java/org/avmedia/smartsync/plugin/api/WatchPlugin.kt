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

    /** Starts the plugin. [host] stays valid until [deactivate]. Declare [PluginHost.declareNeeds] here. */
    suspend fun activate(host: PluginHost)

    /** Stops the plugin: end sockets and jobs. Its queued events and commands are dropped by the host. */
    suspend fun deactivate()

    /** One event. Return when it is handled; long work belongs in the plugin's own scope. */
    suspend fun onEvent(event: WatchEvent)

    /** What the plugin shows in the app; null for none. */
    val ui: PluginUi? get() = null
}

/** The places in the app a plugin may fill. Each is optional. */
interface PluginUi {
    /** A row in the Settings tab (name, status); [onOpen] opens [SettingsScreen]. */
    @Composable
    fun SettingsEntry(onOpen: () -> Unit)

    /** The plugin's own screen, full size; [onBack] closes it (the back gesture does too). */
    @Composable
    fun SettingsScreen(onBack: () -> Unit)

    /** A row in the Actions tab (what a watch button press does in this plugin); [onOpenSettings] opens [SettingsScreen]. */
    @Composable
    fun ActionsRow(onOpenSettings: () -> Unit) {}

    /** False: no Actions row is shown. */
    val hasActionsRow: Boolean get() = false
}

/**
 * What a plugin needs from the host. The host keeps the union over the active plugins: a need of a plugin that is off
 * or failed counts for nothing.
 */
data class PluginNeeds(
    /** Value keys ([WatchValues]) the host should read for this plugin; empty = none, null = every key it knows. */
    val values: Set<String>? = emptySet(),
    /** The plugin wants [WatchEvent.ButtonPressed]: it is listed among the actions a watch button press runs. */
    val buttonPress: Boolean = false,
    /** The plugin uses health data: the host reads the watch's health log in the background as well. */
    val healthReads: Boolean = false,
    /** The plugin needs the link kept up in the background ("Keep connected in background" for that watch). */
    val keepConnected: Boolean = false,
    /** Read every settings code undecoded as well (for decoding work); costs link time on each read. */
    val undecodedSettings: Boolean = false,
)
