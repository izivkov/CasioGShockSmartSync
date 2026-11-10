package org.avmedia.gshockGoogleSync.session

enum class EntryReason {
    NORMAL,
    CONTENT_SELECTOR,
    RUN_ACTIONS;

    companion object {
        /** A normal connection opens the Time screen; every other button resolves a content screen. */
        fun forButton(button: ConnectionButton): EntryReason =
            if (button == ConnectionButton.NORMAL) NORMAL else CONTENT_SELECTOR
    }
}

sealed interface SessionPhase {
    data object Disconnected : SessionPhase
    data object Connecting : SessionPhase
    data class Ready(val entryReason: EntryReason = EntryReason.NORMAL) : SessionPhase
    data class Closed(val reason: String = "") : SessionPhase
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
    val pendingSaves: Set<ComponentId> = emptySet(),
    val errorMessage: String? = null
) {
    val isNormal: Boolean get() = button == ConnectionButton.NORMAL
    val isFromAction: Boolean get() = button == ConnectionButton.FROM_ACTION
    val isAutomaticSync: Boolean get() = button == ConnectionButton.AUTOMATIC_SYNC
    val isFind: Boolean get() = button == ConnectionButton.FIND
    val isAlwaysConnected: Boolean get() = button == ConnectionButton.ALWAYS_CONNECTED

    fun isPendingSave(component: ComponentId): Boolean = component in pendingSaves
    fun hasPendingSaves(): Boolean = pendingSaves.isNotEmpty()
}