package org.avmedia.gshockGoogleSync.session

enum class EntryReason {
    NORMAL,
    CONTENT_SELECTOR,
    RUN_ACTIONS;

    companion object {
        /**
         * Where a connection opens: the Time screen for a normal connection, the run-actions screen
         * for connections started to run actions, and the content selector for the rest.
         */
        fun forButton(button: ConnectionButton): EntryReason = when (button) {
            ConnectionButton.NORMAL -> NORMAL
            ConnectionButton.FROM_ACTION, ConnectionButton.AUTOMATIC_SYNC -> RUN_ACTIONS
            ConnectionButton.FIND, ConnectionButton.ALWAYS_CONNECTED -> CONTENT_SELECTOR
        }
    }
}

sealed interface SessionPhase {
    data object Disconnected : SessionPhase
    data object Connecting : SessionPhase
    /** [entryReason] is the screen the connection is on; see [Session.enterMainNavigation]. */
    data class Ready(val entryReason: EntryReason = EntryReason.NORMAL) : SessionPhase
    /** [isError] marks a failure the user should be told about; [reason] may then be blank. */
    data class Closed(val reason: String = "", val isError: Boolean = false) : SessionPhase
}

data class WatchSnapshot(
    val name: String = "",
    val address: String = "",
    val model: String = ""
)

data class SessionState(
    val phase: SessionPhase = SessionPhase.Disconnected,
    /** Null until the session is Ready. Kept after the session closes. */
    val button: ConnectionButton? = null,
    val watch: WatchSnapshot = WatchSnapshot(),
    /**
     * True once the BLE link is up and the watch has reported its name and address
     * ("ConnectionSetupComplete"). Per-connection data can be loaded from then on.
     */
    val isSetupComplete: Boolean = false,
    /** True while actions requested by an always-connected watch are running. */
    val isRunningActions: Boolean = false,
    val pendingSaves: Set<ComponentId> = emptySet()
) {
    val isNormal: Boolean get() = button == ConnectionButton.NORMAL
    val isFromAction: Boolean get() = button == ConnectionButton.FROM_ACTION
    val isAutomaticSync: Boolean get() = button == ConnectionButton.AUTOMATIC_SYNC
    val isFind: Boolean get() = button == ConnectionButton.FIND
    val isAlwaysConnected: Boolean get() = button == ConnectionButton.ALWAYS_CONNECTED

    fun isPendingSave(component: ComponentId): Boolean = component in pendingSaves
    fun hasPendingSaves(): Boolean = pendingSaves.isNotEmpty()
}
