package org.avmedia.smartsync.plugin.api

import androidx.compose.runtime.Composable

/**
 * A plugin: something the app runs for the watches (a Home Assistant link, a Health Connect writer, a webhook). Found
 * by the host through `META-INF/services/org.avmedia.smartsync.plugin.api.WatchPlugin` (java.util.ServiceLoader), so
 * it needs a public constructor without arguments.
 *
 * Threading and limits: the host calls [activate], [deactivate] and [onEvent] off the main thread, one at a time per
 * plugin, each with a time limit of at least: [activate] 15 s, [onEvent] 30 s, [deactivate] 5 s. Time spent waiting in
 * [PluginHost] calls does not count. A call over its limit is cancelled; one that ignores the cancellation (blocked in a
 * socket or a lock) turns the plugin off at once. An exception thrown here (also a CancellationException of the
 * plugin's own) is caught and counted; 5 failures in a row turn the plugin off and Settings says so.
 *
 * Events come one at a time and in order, on a bounded queue of this plugin's own (64 at least): a slow plugin delays
 * only itself. When the queue is full the oldest events that a newer one makes stale are dropped first, and the
 * plugin gets [WatchEvent.EventsLost]. Events are not kept across a process death: in [activate] a plugin reconciles
 * from [PluginHost.history] and [PluginHost.lastValues] instead of relying on events it may have missed.
 */
interface WatchPlugin {
    /** Stable id, lower case letters, digits and `_` (for example "home_assistant"). Names its settings and storage. */
    val id: String

    /** The name the person sees (Settings, Actions). */
    val name: String

    /** One short line about what it does, for its row in Settings > Plugins; "" for none. */
    val summary: String get() = ""

    /** Two or three plain sentences of what it can do, first on its details page; "" for none (the [summary] shows). */
    val description: String get() = ""

    /** One short line of what the person must have, for "Needs: ..." ("Your Home Assistant URL and a token"); "" for none. */
    val setupNeeds: String get() = ""

    /** Its guide or documentation (https), the "Guide" link of its details page; null for none. */
    val infoUrl: String? get() = null

    /** Its source code (https); null for none. */
    val sourceUrl: String? get() = null

    /** Where to report a problem (https); null for none. */
    val issuesUrl: String? get() = null

    /** Who made it ("ExcuseMi"); "" for none. */
    val author: String get() = ""

    /** Its licence, an SPDX id ("MIT", "GPL-3.0-or-later"); "" for none. */
    val license: String get() = ""

    /** The interface version this plugin was built against: write `override val apiVersion = PluginApi.VERSION`. */
    val apiVersion: String

    /** The plugin's own version, shown in Settings > Plugins ("0.1.0"). */
    val version: String

    /** What the plugin reads, controls, sends where and keeps ([PluginAccess]); the host keeps it to that. */
    val access: PluginAccess

    /** Its icon in Settings, its Actions card and the App log; null: the host's plugin icon. */
    val icon: PluginIcon? get() = null

    /**
     * Starts the plugin. [host] is valid until [deactivate] and revoked after it ([PluginHost]). Declare
     * [PluginHost.declareNeeds] here. Events that happen meanwhile are queued and come after this returns.
     */
    suspend fun activate(host: PluginHost)

    /**
     * Stops the plugin: end sockets and jobs. Never called while [onEvent] runs (the host cancels a running one and waits
     * up to 5 s first). Its queued events and commands are dropped by the host.
     */
    suspend fun deactivate()

    /** One event. Return when it is handled; long work belongs in the plugin's own scope. */
    suspend fun onEvent(event: WatchEvent)

    /** What the plugin shows in the app; null for none. */
    val ui: PluginUi? get() = null

    /**
     * A permission flow of its own (Health Connect's permission screen): the host starts this intent instead of the
     * runtime request, then checks again. Null: the host asks for the runtime permissions itself.
     */
    fun permissionIntent(context: android.content.Context): android.content.Intent? = null

    /**
     * Which of its own [PluginAccess.androidPermissions] are missing, for permissions the host cannot check itself
     * (Health Connect before Android 14: `getGrantedPermissions()`). Null: the host checks each one with the system.
     * The host calls it off the main thread with a limit of 5 s (at start, after a turn-on, after an answer, when the
     * app comes to the front) and keeps the answer; a call over the limit keeps the answer before. It counts only for
     * names in [PluginAccess.androidPermissions]: the host checks the location permission itself.
     */
    suspend fun missingPermissions(context: android.content.Context): Set<String>? = null
}

/**
 * What a plugin shows in the app. The host lists it in Settings > Plugins (icon, name, version, the text of its last
 * [PluginStatus.Ok], the on/off switch, a details page with its [PluginAccess]; its screen opens from there) and, with
 * [hasActionsRow], in the Actions tab (icon, name, the same switch, and below it [ActionsRow] while the plugin is on).
 * Screens run in the app's theme.
 *
 * The composables run on the main thread and must not block (no storage writes: [PluginStorage] may block). They may
 * run while the plugin is off, before [WatchPlugin.activate] or after [WatchPlugin.deactivate]: show what can be shown
 * then. An exception thrown in a composable is not caught: Compose cannot isolate it, so it ends the app.
 */
interface PluginUi {
    /** The plugin's own screen, full size; [onBack] closes it (the back gesture does too). */
    @Composable
    fun SettingsScreen(onBack: () -> Unit)

    /** True: the host opens [SettingsScreen] each time the plugin is turned on, once it is active (to ask for permissions). */
    val opensOnTurnOn: Boolean get() = false

    /** True: the plugin has a card in the Actions tab. */
    val hasActionsRow: Boolean get() = false

    /** The plugin's part of its Actions card (what a watch button press does); [onOpenSettings] opens [SettingsScreen]. */
    @Composable
    fun ActionsRow(onOpenSettings: () -> Unit) {}
}

/**
 * What a plugin needs from the host. The host keeps the union over the active plugins: a need of a plugin that is off
 * or failed counts for nothing, and so does a need its [PluginAccess] does not cover. This type gets no new field in
 * a compatible release (README): a new need comes as a new type and host call.
 */
data class PluginNeeds(
    /**
     * Value keys ([PluginHost.valueCatalog]) the host should read for this plugin; empty = none, null = every key of a
     * kind it declared ([PluginAccess.reads]; the full read takes link time on every connection). The union is read:
     * a plugin with an empty set still gets [WatchEvent.ValuesUpdated] when another plugin asked for values.
     */
    val values: Set<String>? = emptySet(),
    /**
     * The host reads the watch settings of the catalogue ([PluginHost.settingCatalog]) at each connection, for
     * [PluginHost.settings] (needs [DataUse.WATCH_SETTINGS] or [DataUse.BODY_PROFILE]; only the rows of the declared
     * kind are served). False: [PluginHost.settings] holds only what another plugin happened to ask for.
     */
    val settings: Boolean = false,
    /** The plugin wants [WatchEvent.ButtonPressed] (needs [Control.BUTTON_PRESS]): it is listed among the actions a press runs. */
    val buttonPress: Boolean = false,
    /** The plugin uses health data (needs a health [DataUse]): the host reads the watch's health log in the background as well. */
    val healthReads: Boolean = false,
    /**
     * Send [WatchEvent.Wake] about every this many minutes (15 at least; null = never), also when the app was stopped:
     * the host's one periodic job starts the plugins first. Not exact: Android may delay it (Doze). The host keeps the
     * process (and the job's network) until [WatchPlugin.onEvent] of that [WatchEvent.Wake] returns, 60 s at most.
     */
    val wakeEveryMinutes: Int? = null,
)
