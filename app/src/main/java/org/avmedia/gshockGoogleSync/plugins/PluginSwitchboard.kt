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
) {
    fun isOn(id: String): Boolean = toggles.isOn(id, default = false) // RED CHECK

    /** At app start: every plugin that is switched on. */
    suspend fun startAll() {
        for (p in runtime.plugins()) if (isOn(p.id)) activate(p.id)
    }

    /** The person's switch. On again also clears a failure and its count. */
    suspend fun set(id: String, on: Boolean) {
        // RED CHECK: not kept
        if (on) activate(id) else runtime.deactivate(id)
    }

    private suspend fun activate(id: String) {
        if (runtime.activate(id)) afterActivate(id)
    }
}
