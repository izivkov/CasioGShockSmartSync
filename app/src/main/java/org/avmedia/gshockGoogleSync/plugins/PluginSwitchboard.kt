package org.avmedia.gshockGoogleSync.plugins

/**
 * Each plugin's on/off switch: stored by [toggles] (it survives restarts), On activates, Off deactivates. A plugin found
 * for the first time is off, except Home Assistant for a phone where it was set up before the plugin interface
 * ([legacyOn]). Android-free.
 */
class PluginSwitchboard(
    private val toggles: PluginToggles,
    private val runtime: PluginRuntime,
    private val legacyOn: (String) -> Boolean = { false },
    /** After a plugin started: what it missed (the background service, the connected watch). */
    private val afterActivate: suspend (String) -> Unit = {},
    /** The person turned it on and it started (not at app start): ask for what it needs. */
    private val afterTurnOn: (String) -> Unit = {},
    /** Whether a plugin wants its screen each time it is turned on ([org.avmedia.smartsync.plugin.api.PluginUi.opensOnTurnOn]). */
    private val opensOnTurnOn: (String) -> Boolean = { false },
    /**
     * Opens a plugin's screen: after the person turned it on and it started, from any switch (Settings > Plugins or
     * the Actions tab: both end here). Never for a turn-off or a plugin that failed to start.
     */
    private val open: (String) -> Unit = {},
) {
    fun isOn(id: String): Boolean = toggles.isOn(id, default = legacyOn(id))

    /** At app start: every plugin that is switched on. */
    suspend fun startAll() {
        for (id in runtime.ids()) if (isOn(id)) activate(id)
    }

    /** The person's switch. On again also clears a failure and its count. */
    suspend fun set(id: String, on: Boolean) {
        toggles.set(id, on)
        if (!on) runtime.deactivate(id)
        else if (activate(id)) {
            afterTurnOn(id)
            if (runCatching { opensOnTurnOn(id) }.getOrDefault(false)) open(id)
        }
    }

    private suspend fun activate(id: String): Boolean = runtime.activate(id).also { if (it) afterActivate(id) }
}
